package com.ec.expresscheck.controller.venue

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.Toolbar
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.ec.expresscheck.App
import com.ec.expresscheck.R
import com.ec.expresscheck.controller.base.BackPressController
import com.ec.expresscheck.model.ChatDto
import com.ec.expresscheck.model.OrderComment
import com.ec.expresscheck.model.lab.OrderLab
import com.ec.expresscheck.network.Wrapper
import com.ec.expresscheck.network.dto.SendCommentBody
import com.ec.expresscheck.util.*
import com.ec.expresscheck.view.CustomProgressBar
import kotlinx.android.synthetic.main.controller_chat.*
import kotlinx.android.synthetic.main.include_appbar_eo.*
import kotlin.properties.Delegates

class ChatController(bundle: Bundle?) : BackPressController(bundle) {

    constructor(dto: ChatDto) : this(
        BundleBuilder(Bundle())
            .putSerializable(EXTRA_DTO, dto)
            .build()
    )

    private var venueId by Delegates.notNull<Long>()
    private var orderId by Delegates.notNull<Long>()
    private var adapter: ChatAdapter? = null

    private val delayMillis = 1000L

    private val progress = CustomProgressBar()


    override fun onOtherMenuItemSelected(item: MenuItem): Boolean {
        return true
    }

    private val incomeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val comment = intent.getSerializableExtra(EXTRA_COMMENT) as OrderComment?
            if (comment != null && orderId != comment.order_id) return
            if (comment != null) {
                addMessage(comment)
            }
        }
    }

    private fun addMessage(message: OrderComment) {

        var index = -1
        if (adapter != null) {
            if (!message.isMy()) {
                addItem(message)
            } else {
                index = adapter?.setItem(message) ?: -1
                if (index == -1) {
                    addItem(message)
                }
            }
        }
        val finalIndex = index
        activity.runIfAlive {
            if (adapter == null) {
                recycler_view?.adapter = null
            } else {
                if (recycler_view?.adapter == null) {
                    recycler_view?.adapter = adapter
                }
                if (finalIndex > -1) {
                    adapter?.notifyItemChanged(index)
                } else {
                    val last = adapter?.itemCount?.minus(1) ?: 0
                    adapter?.notifyItemInserted(last)
                    recycler_view?.post { recycler_view?.smoothScrollToPosition(last) }
                    if (!message.isMy())
                        Handler().postDelayed({
                            Wrapper.readComments(venueId, orderId, longArrayOf(message.id))
                        }, delayMillis)
                }
            }
        }
    }

    private fun addItem(message: OrderComment) {
        adapter?.addItem(message)
    }

    private val keyboardHiddenReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            activity?.runIfAlive {
                message_button.visible()
                text_message.invisible()
                button_send.invisible()
            }
        }
    }

    override fun onContextAvailable(context: Context) {
        super.onContextAvailable(context)
        LocalBroadcastManager.getInstance(App.instance).registerReceiver(
            keyboardHiddenReceiver, IntentFilter(
                ACTION_KEYBOARD_HIDDEN
            )
        )
        LocalBroadcastManager.getInstance(App.instance).registerReceiver(
            incomeReceiver, IntentFilter(
                ACTION_PREFIX + NotificationType.ORDER_COMMENT.name
            )
        )
    }

    override fun onContextUnavailable() {
        LocalBroadcastManager.getInstance(App.instance).unregisterReceiver(keyboardHiddenReceiver)
        LocalBroadcastManager.getInstance(App.instance).unregisterReceiver(incomeReceiver)
        super.onContextUnavailable()
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {

        val dto = args.getSerializable(EXTRA_DTO) as ChatDto
        venueId = dto.venueId
        orderId = dto.orderId
        return inflater.inflate(R.layout.controller_chat, container, false)

    }

    override fun initToolbar(): Toolbar? = toolbar

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        venue_name?.text = OrderLab.order?.venue_name

        (recycler_view?.layoutManager as LinearLayoutManager).isSmoothScrollbarEnabled = true

        getComments()

        message_button?.setOnClickListener {
            it.invisible()
            text_message.visible()
            button_send.visible()
            text_message?.requestFocus()
            val imm =
                ServiceUtil.getInputMethodManager(App.instance)
            imm
                ?.toggleSoftInput(
                    InputMethodManager.SHOW_IMPLICIT,
                    InputMethodManager.HIDE_NOT_ALWAYS
                )
        }

        button_send?.setOnClickListener {
            if (text_message?.string().isNullOrEmpty()) return@setOnClickListener
            val text = text_message.stringSafe()

            Wrapper.sendComment(venueId, orderId, SendCommentBody(text),
                callback = {
                    if (it != null) {
                        addMessage(it)
                        text_message?.setText("")
                    }
                },
                error = {
                    activity?.showError(it)
                })

        }

    }

    private fun getComments() {
        progress.show(activity)

        Wrapper.orderComments(
            venueId, orderId, callback = { response ->

                if (response == null)
                    return@orderComments
//
//
                val comments = response.comments


//                val now = DateTime.now().millis
//                val comments = mutableListOf(
//                    OrderComment(
//                        order_id = orderId,
//                        venue_id = venueId,
//                        comment = "Test comment1",
//                        patron_id = 1214L,
//                        read = false,
//                        name = "test",
//                        id = 1,
//                        create_date = now
//                    ),
//                    OrderComment(
//                        order_id = orderId,
//                        venue_id = venueId,
//                        comment = "Test comment2 the long one what if this comment will be three lines long i don't know. Is it possible?",
//                        user_id = 1214L,
//                        read = false,
//                        name = "test",
//                        id = 1,
//                        create_date = DateTime.now().withMinuteOfHour(5).millis
//                    )
//                )
                order_text?.text = response.order_description
//                order_text?.text = "Blah blah"
                if (comments.isNullOrEmpty())
                    return@orderComments

                comments.sortedByDescending { c -> c.create_date }

                if (adapter == null) {
                    adapter = ChatAdapter(comments, activity)
                } else {
                    adapter?.setList(comments)
                }
                recycler_view?.adapter = adapter

                progress.dialog.dismiss()

                val layoutManager = recycler_view?.layoutManager as? LinearLayoutManager

                Handler().postDelayed({

                    if (layoutManager?.findFirstCompletelyVisibleItemPosition() != 0) {

                        var index = adapter?.itemCount?.minus(1) ?: 0
                        index = comments.firstOrNull { c -> c.read == false }
                            ?.let { comments.indexOf(it) } ?: index

                        recycler_view?.postDelayed({
                            recycler_view?.smoothScrollToPosition(
                                index
                            )
                        }, 100)
                    }


                    Handler().postDelayed(
                        {
                            val commentIdList =
                                comments.filter { c -> !c.isMy() }.map { c -> c.id }.toLongArray()
                            Wrapper.readComments(venueId, orderId, commentIdList)
                        },
                        delayMillis
                    )
                }, 300)

            },
            error = {
                progress.dialog.dismiss()
                activity?.alert(it?.error_description)
            })

    }


}


