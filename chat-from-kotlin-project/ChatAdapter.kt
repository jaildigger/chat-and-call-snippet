package com.ec.expresscheck.controller.venue

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.ec.expresscheck.R
import com.ec.expresscheck.model.OrderComment
import com.ec.expresscheck.util.getTimestamp
import kotlinx.android.synthetic.main.item_incoming_message.view.message as incomingMessage
import kotlinx.android.synthetic.main.item_incoming_message.view.timestamp as incomingTime
import kotlinx.android.synthetic.main.item_outgoing_message.view.message as outgoingMessage
import kotlinx.android.synthetic.main.item_outgoing_message.view.timestamp as outgoingTime


class ChatAdapter constructor(
    var items: MutableList<OrderComment>,
    val context: Context?
) :
    androidx.recyclerview.widget.RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val inflater = LayoutInflater.from(context)

        return when (MessageViewType.values()[viewType]) {
            MessageViewType.INCOMING -> IncomingHolder(inflater, parent)
            MessageViewType.OUTGOING -> OutgoingHolder(inflater, parent)
        }

    }

    fun setList(list: MutableList<OrderComment>) {
        this.items = list
        notifyDataSetChanged()
    }

    fun addItem(item: OrderComment) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    fun setItem(data: OrderComment): Int {
        return if (items.isEmpty()) {
            items.add(data)
            0
        } else {
            val index = items.indexOfFirst { m -> m.create_date == data.create_date }
            if (index > -1) {
                items[index] = data
            }
            index
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size


    open inner class ViewHolder(itemView: View) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        open fun bind(item: OrderComment?) = with(itemView) {}
    }


    override fun getItemViewType(position: Int): Int {
        return when (items[position].isMy()) {
            true -> MessageViewType.OUTGOING.ordinal
            else -> MessageViewType.INCOMING.ordinal
        }
    }


    inner class IncomingHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup?) :
        ViewHolder(
            inflater.inflate(
                R.layout.item_incoming_message,
                parent,
                false
            )
        ) {

        override fun bind(item: OrderComment?) = with(itemView) {
            incomingMessage?.text = item?.comment
            incomingTime?.text = getTimestamp(item?.create_date)
        }
    }

    inner class OutgoingHolder internal constructor(inflater: LayoutInflater, parent: ViewGroup?) :
        ViewHolder(
            inflater.inflate(
                R.layout.item_outgoing_message,
                parent,
                false
            )
        ) {

        override fun bind(item: OrderComment?) = with(itemView) {
            outgoingMessage?.text = item?.comment
            outgoingTime?.text = getTimestamp(item?.create_date)
        }
    }

}


enum class MessageViewType {
    INCOMING, OUTGOING
}