package rent.auto.chats;

import static rent.auto.util.Broadcast.ACTION_NEW_BOOKING;
import static rent.auto.util.Broadcast.ACTION_NEW_MESSAGE;
import static rent.auto.util.Broadcast.EXTRA_BOOKING_ID;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rent.auto.Analytics;
import rent.auto.App;
import rent.auto.BackPressFragment;
import rent.auto.R;
import rent.auto.model.Bill;
import rent.auto.model.BookingData;
import rent.auto.model.ChatData;
import rent.auto.model.ChatMessages;
import rent.auto.model.Message;
import rent.auto.model.NewMessage;
import rent.auto.model.constant.BookingStatus;
import rent.auto.model.constant.ChatEvent;
import rent.auto.model.lab.BookingLab;
import rent.auto.model.view.CustomProgressBar;
import rent.auto.model.view.KeyboardDismissingRecyclerView;
import rent.auto.socket.Api;
import rent.auto.socket.ChatEventsManager;
import rent.auto.socket.PaymentResponse;
import rent.auto.socket.ResponseAdapter;
import rent.auto.socket.request.YooKassaRequest;
import rent.auto.util.Broadcast;
import rent.auto.util.GlideApp;
import rent.auto.util.Helpers;
import rent.auto.util.Preferences;
import rent.auto.util.ServiceUtil;
import rent.auto.util.UiUtils;
import rent.auto.webrtc.CallAction;
import rent.auto.webrtc.CallActivity;
import ru.yoomoney.sdk.kassa.payments.Checkout;
import ru.yoomoney.sdk.kassa.payments.TokenizationResult;
import ru.yoomoney.sdk.kassa.payments.checkoutParameters.Amount;
import ru.yoomoney.sdk.kassa.payments.checkoutParameters.PaymentMethodType;
import ru.yoomoney.sdk.kassa.payments.checkoutParameters.PaymentParameters;
import ru.yoomoney.sdk.kassa.payments.checkoutParameters.SavePaymentMethod;
import ru.yoomoney.sdk.kassa.payments.checkoutParameters.TestParameters;


public class ChatFragment extends BackPressFragment implements ChatAdapter.PaymentCallback {

    private final static String ARG_BOOKING_ID = "booking_id";
    private static final String EXTRA_JSON = "rent.auto.extra.json";
    private final static String ARG_TITLE = "title";
    private final static String ARG_CAR_TITLE = "car_title";
    private final static String ARG_LOCKED = "locked";
    private final static String ARG_PC_URL = "picture_url";
    private final static String ARG_CONTAINER = "container_id";
    private static final String OPEN = "open";
    private static final String CLOSED = "closed";
    private static final int DELAY_MILLIS = 1000;


    private ChatMessages chatMessages;
    private BookingData booking;
    private Callbacks callbacks;
    private ChatEventsManager eventsManager = null;
    private ChatEvent chatStatus = ChatEvent.NOTHING;
    private boolean isPaid = false;
    private ChatAdapter adapter;
    private Long bookingId = 0L;
    private String title = "";
    private String carTitle = "";
    private boolean isDocsAlreadySent = false;

    private boolean isLocked;
    private boolean isFirstWasVisible = false;
    @IdRes
    private int containerId;

    private static final int REQUEST_CODE_TOKENIZE = 890;
    private static final int REQUEST_CODE_END_CALL = 74;
    private static final int REQUEST_CODE_3D_SECURE = 891;
    private PaymentParameters paymentParameters = null;
    private CustomProgressBar progressWindow = new CustomProgressBar();
    //BOTTOM

    private boolean isClient = false;

    private final View.OnClickListener closeListener = view -> runIfAlive(() -> this.hintsLayout.setVisibility(View.INVISIBLE));
    private final BroadcastReceiver cancelReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Long receivedId = intent.getLongExtra(EXTRA_BOOKING_ID, 0);
            if (!bookingId.equals(receivedId))
                return;
            runIfAlive(() -> {
                if (chatMessages != null && chatMessages.getData() != null)
                    chatMessages.getData().setBookingStatus(BookingStatus.CANCELED);
                if (buttonAttach.getTag().equals(OPEN))
                    buttonAttach.performClick();
                updateBookingUi();
            });
        }
    };

    private void hideProgressWindow() {
        if (progressWindow == null)
            return;
        runIfAlive(() -> progressWindow.getDialog().dismiss());

    }

    private final BroadcastReceiver incomeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {

            String json = intent.getStringExtra(EXTRA_JSON);
            NewMessage newMessage = NewMessage.get(json);

            if (newMessage != null && !bookingId.equals(newMessage.getBookingId()))
                return;
            if (newMessage != null && chatMessages != null) {
                chatMessages.setData(newMessage.getChatData());
                if (adapter != null) {
                    adapter.updateChatData(newMessage.getChatData());
                }
                addMessage(newMessage.getMessage());
            }

        }
    };
    private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
        }

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

//            Log.d(App.TAG, String.valueOf(dy));
            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            if (layoutManager != null && layoutManager.findFirstCompletelyVisibleItemPosition() == 0 && !isFirstWasVisible && dy < 0) {
                detailsLayoutToggle(true);
                return;
            }
            if (dy > 0 && !isDetailsCollapsed) {
                detailsLayoutToggle(false);
            }
        }
    };

    private final BroadcastReceiver keyboardHiddenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            if (getContext() == null)
                return;

            runIfAlive(() -> {
                if (buttonMessage != null)
                    buttonMessage.setVisibility(View.VISIBLE);
                if (textMessage != null)
                    textMessage.setVisibility(View.INVISIBLE);
                if (buttonSend != null)
                    buttonSend.setVisibility(View.INVISIBLE);
            });
        }


    };


    private void addItem(Message message) {
        adapter.addItem(message);
        LinearLayoutManager layoutManager = (LinearLayoutManager) messagesView.getLayoutManager();

        if (Objects.requireNonNull(layoutManager).findFirstCompletelyVisibleItemPosition() == 0) {
            runIfAlive(() -> toolbarLayout.setExpanded(true, true));
        } else {
            toolbarLayout.setExpanded(false, true);
        }
    }

    private void showProgressWindow() {
        if (progressWindow == null) {
            progressWindow = new CustomProgressBar();
        }
        runIfAlive(() -> progressWindow.show(requireActivity()));
    }


    public static ChatFragment newInstance(ChatParams chatParams) {
        ChatFragment fragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_BOOKING_ID, chatParams.getBookingId());
        args.putString(ARG_TITLE, chatParams.getTitle());
        args.putString(ARG_CAR_TITLE, chatParams.getCarTitle());
        args.putBoolean(ARG_LOCKED, chatParams.isLocked());
        args.putString(ARG_PC_URL, chatParams.getPictureUrl());
//        args.putInt(ARG_CONTAINER, chatParams.getContainerId());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        callbacks = (Callbacks) context;
        IntentFilter filter = new IntentFilter(ACTION_NEW_MESSAGE);
        LocalBroadcastManager.getInstance(context).registerReceiver(incomeReceiver, filter);
        IntentFilter filter2 = new IntentFilter(Broadcast.ACTION_KEYBOARD_HIDDEN);
        LocalBroadcastManager.getInstance(context).registerReceiver(keyboardHiddenReceiver, filter2);
        IntentFilter filter3 = new IntentFilter(ACTION_NEW_BOOKING);
        LocalBroadcastManager.getInstance(context).registerReceiver(cancelReceiver, filter3);
    }

    private ChatEventsManager.ChatEventsSignal signal = (event, data) -> {

        this.chatStatus = event;
        if (data != null && data.getBookingStartDate() != null)
            chatMessages.setData(data);
        if (adapter != null)
            adapter.updateChatStatus(chatStatus, data);

        runIfAlive(() -> {
            switch (event) {
                case ENTER_CHAT:
                    chatMessages.getData().setOnline(true);
                    setOnlineStatus();
                    break;
                case STATUS_PAYED:
                    isPaid = true;
                    this.buttonCancelBook.setEnabled(false);
                    this.buttonCancelBook.setVisibility(View.GONE);
                    disableBillButton();
                    this.statusView.setText(R.string.status_paid);
                    break;
                case BILLED_START:
                    cancelPayment();
                    break;
                case PAYMENT_START:
                    disableBillButton();
                    break;
                case LEAVE_CHAT:
                    chatMessages.getData().setOnline(false);
                    setOnlineStatus();
                    if (chatMessages.getData().getBookingStatus() != BookingStatus.PAID)
                        enableBillButton();
                    break;
                case PAYMENT_END:
                    enableBillButton();
                    break;
                default:
                    break;

            }


        });


    };

    private void addMessage(Message message) {
        int index = -1;
        if (adapter != null) {

            if (!message.isDeliveredByServer() || !message.isMy() || message.isMy() && message.getAuthorId() == 0) {
                addItem(message);
            } else {
                index = adapter.setItem(message);
                if (index == -1) {
                    addItem(message);
                }
            }
        }

        int finalIndex = index;
        runIfAlive(() -> {

            if (adapter == null) {
                messagesView.setAdapter(null);
            } else {
                if (messagesView.getAdapter() == null) {
                    messagesView.setAdapter(adapter);
                }
                if (finalIndex > -1) {
                    adapter.notifyItemChanged(finalIndex);
                } else {
                    adapter.notifyItemInserted(adapter.getItemCount() - 1);
                    messagesView.post(() -> messagesView.smoothScrollToPosition(adapter.getItemCount() - 1));
                    new Handler().postDelayed(() -> App.socket().messagesSetRead(bookingId), DELAY_MILLIS);

                }
            }

        });

    }

    @Override
    public void onDetach() {
        super.onDetach();
        callbacks = null;
        if (adapter != null) {
            adapter.resetCallbacks();
        }
        signal = null;
        for (BroadcastReceiver receiver : Arrays.asList(incomeReceiver, keyboardHiddenReceiver, cancelReceiver, eventsManager)) {
            if (receiver != null)
                LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver);
        }
    }

    @OnClick({R.id.text_message, R.id.button_message})
    void onClickTextMessage() {
        if (buttonAttach.getTag().equals(OPEN)) {
            runIfAlive(this::onButtonAttach);
        }
    }

    @OnClick(R.id.button_message)
    void onMessageClick() {
        buttonMessage.setVisibility(View.INVISIBLE);
        textMessage.setVisibility(View.VISIBLE);
        buttonSend.setVisibility(View.VISIBLE);
        textMessage.requestFocus();
        InputMethodManager imm = ServiceUtil.getInputMethodManager(requireActivity());
        Objects.requireNonNull(imm).toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_NOT_ALWAYS);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            bookingId = getArguments().getLong(ARG_BOOKING_ID);
            title = getArguments().getString(ARG_TITLE);
            carTitle = getArguments().getString(ARG_CAR_TITLE);
            isLocked = getArguments().getBoolean(ARG_LOCKED);
            containerId = getArguments().getInt(ARG_CONTAINER);

        }


    }

    @OnClick(R.id.button_details)
    void onButtonDetails() {
        if (chatMessages == null || chatMessages.getData() == null)
            return;
        callbacks.onClickButtonDetails(bookingId, chatMessages.getData().getUserRole(), containerId);
    }

    @OnClick(R.id.button_send)
    void onMessageSend() {
        if (TextUtils.isEmpty(textMessage.getText()))
            return;
        String text = Objects.requireNonNull(textMessage.getText()).toString();

        ChatApiWrapper.sendMessage(getActivity(), bookingId, text, message -> {
//            addMessage(message);
            textMessage.setText("");
        });

    }

    private void updateBookingUi() {
        if (chatMessages == null) return;
        final ChatData data = chatMessages.getData();
        if (getActivity() != null)
            GlideApp.with(requireActivity()).load(data.getCarPic()).into(orderPhoto);
        statusView.setText(Helpers.visibleStatus(data.getBookingStatus(), data.getBookingEndDate()).getValue());
        if (getContext() != null)
            periodView.setText(Helpers.formatPeriod(requireContext(), data.getBookingStartDate(), data.getBookingEndDate()));
        buttonDetails.setEnabled(true);
        checkDocs();

    }

    private void getBookingDetails() {
        ChatApiWrapper.getBookingDetails(getActivity(), bookingId, isClient, bookingData -> {
            this.booking = bookingData;
            updateBookingUi();
        });

    }

    private void checkDocs() {
        final ChatData data = chatMessages.getData();

        if (BookingStatus.CANCELED == data.getBookingStatus()) {
            return;
        }

        ChatApiWrapper.docsBookingCall(getActivity(), bookingId, docs -> {

            if (isClient) {
                if (docs != null && docs.size() > 0) {
                    isDocsAlreadySent = true;
                    if (adapter != null) {
                        adapter.setDocsSent();
                    }

                } else {
                    buttonDocs.setText(R.string.upload_docs);
                    isDocsAlreadySent = false;
                    if (adapter != null) {
                        adapter.setDocsNotSent();
                    }
                }
                buttonDocs.setOnClickListener(v -> callbacks.onClickUploadDocs(booking, containerId));
            } else {

                if (docs != null && docs.size() > 0 ||
                        BookingStatus.COMPLETE == Helpers.visibleStatus(data.getBookingStatus(), data.getBookingEndDate()) ||
                        booking.getDocumentStatus1() != null && booking.getDocumentStatus1().equals("required") ||
                        booking.getDocumentStatus2() != null && booking.getDocumentStatus2().equals("required")) {
                    isDocsAlreadySent = true;
                    if (adapter != null) {
                        adapter.setDocsSent();
                    }

                } else {
                    buttonDocs.setText(R.string.request_docs);
                    isDocsAlreadySent = false;
                    if (adapter != null) {
                        adapter.setDocsNotSent();
                    }
                    buttonDocs.setOnClickListener(v -> App.socket().docRequest(bookingId, new ResponseAdapter(getActivity()) {
                        @Override
                        public void onSuccess(Api apiName, String json) {
                            isDocsAlreadySent = true;
                            runIfAlive(() -> {
                                if (adapter != null) {
                                    adapter.setDocsSent();
                                }
                                buttonAttach.performClick();
                            });
                        }
                    }));
                }
            }
        });


        buttonDocs.setEnabled(true);
    }

    private void cancelBooking() {

        runIfAlive(() -> progressBar.setVisibility(View.VISIBLE));

        if (bookingId == 0L)
            return;

        ChatApiWrapper.cancelBooking(getActivity(), bookingId, isClient, () -> {

            if (isClient) {
                BookingLab.get().remove(bookingId);
                chatMessages.getData().setBookingStatus(BookingStatus.CANCELED);
                LocalBroadcastManager.getInstance(requireContext()).
                        sendBroadcast(new Intent(Broadcast.ACTION_REMOVE_BOOKING));
                Analytics.INSTANCE.sendEvent("CANCEL_BOOKING", "");
            } else {
                chatMessages.getData().setBookingStatus(BookingStatus.CANCELED);
                disableBillButton();
            }
            if (adapter != null) {
                adapter.updateChatStatus(ChatEvent.NOTHING, chatMessages.getData());
            }

            buttonAttach.performClick();
            updateBookingUi();
            manageToolbarScrolling();

        }, this::hideProgress);


    }


    private boolean isDetailsCollapsed = false;


    private void showProgress() {
        progressBar.setVisibility(View.VISIBLE);

    }

    private void hideProgress() {
        progressBar.setVisibility(View.INVISIBLE);
    }

    @OnClick(R.id.button_attach)
    void onButtonAttach() {


        TypedValue tv = new TypedValue();
        int actionBarHeight = 0;
        if (requireActivity().getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
        }
        if (buttonAttach.getTag().equals(OPEN)) {
            buttonAttach.setEnabled(false);
            buttonCancelBook.setVisibility(View.GONE);
            buttonDocs.setVisibility(View.GONE);
            buttonAttach.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_more));
            CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) messagesView.getLayoutParams();
            params.setMargins(0, dpToPx(8), 0, actionBarHeight);
            messagesView.requestLayout();
            buttonAttach.setTag(CLOSED);
            buttonAttach.setEnabled(true);
        } else {
            if (booking == null)
                return;
            final ChatData data = chatMessages.getData();

            buttonAttach.setEnabled(false);
            buttonAttach.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_more_black));
            DateTime checkIn = new DateTime(data.getBookingStartDate());

            if (BookingStatus.CANCELED != data.getBookingStatus() && (isClient && checkIn.minusDays(2).isAfterNow() ||
                    !isClient && checkIn.isAfterNow())
            ) {
                buttonCancelBook.setVisibility(View.VISIBLE);
                buttonCancelBook.setOnClickListener(v -> {
                    AlertDialog dialog = new AlertDialog.Builder(getContext()).setMessage(R.string.book_cancel_prompt).
                            setNegativeButton(android.R.string.no, ((dialog1, which) -> dialog1.dismiss())).
                            setPositiveButton(android.R.string.yes, (dialog1, which) -> runIfAlive(this::cancelBooking)).create();
                    dialog.show();
                });
            } else {
                buttonCancelBook.setVisibility(View.GONE);
            }


            boolean cancelIsVisible = buttonCancelBook.getVisibility() == View.VISIBLE;
            int coef = 3;
            if (!cancelIsVisible && isDocsAlreadySent) {
                coef = 1;
            } else if (!cancelIsVisible || isDocsAlreadySent) {
                coef = 2;
            }
            if (!isDocsAlreadySent)
                buttonDocs.setVisibility(View.VISIBLE);

            CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) messagesView.getLayoutParams();
            params.setMargins(0, dpToPx(8), 0, actionBarHeight * coef);
            messagesView.requestLayout();
            buttonAttach.setTag(OPEN);
            buttonAttach.setEnabled(true);
        }

    }

    private void manageToolbarScrolling() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) messagesView.getLayoutManager();
        new Handler().postDelayed(() -> {
            if (Objects.requireNonNull(layoutManager).findFirstCompletelyVisibleItemPosition() == 0) {
                detailsLayoutToggle(true);
            } else {
                runIfAlive(() -> {
                    detailsLayoutToggle(false);
                    messagesView.postDelayed(() -> messagesView.smoothScrollToPosition(adapter.getItemCount() - 1), 100);
                });

            }
        }, 100);
    }

    @Override
    public void onClickPay(Long bookingId, Bill bill) {
        showProgressWindow();
        App.socket().sendChatEvent(bookingId, ChatEvent.PAYMENT_START);
        if (adapter != null)
            adapter.updateChatStatus(ChatEvent.PAYMENT_START);

        final ChatData data = chatMessages.getData();

        Integer price = data.getBookingCurrency().equalsIgnoreCase("RUB") ? bill.getPriceWithCommission() : data.getBookingPriceWithCommissionRub();

        paymentParameters = new PaymentParameters(
                new Amount(BigDecimal.valueOf(price), Currency.getInstance("RUB")),
                getString(R.string.payment_title, data.getCarTitle()),
                getString(R.string.payment_subtitle, Helpers.formatPeriodFull(requireContext(), data.getBookingStartDate(), data.getBookingEndDate()),
                        bookingId),
                getString(R.string.yookassa_key),
                getString(R.string.yookassa_shop_id), SavePaymentMethod.USER_SELECTS,
                Collections.singleton(PaymentMethodType.BANK_CARD)
        );

        TestParameters testParameters = new TestParameters(true);
        Intent intent = Checkout.createTokenizeIntent(requireContext(), paymentParameters, testParameters);
        startActivityForResult(intent, REQUEST_CODE_TOKENIZE);

    }

    @Override
    public void onClickChange() {
        onBillRequestClick(buttonRequestBill);
    }

    private boolean handleCanceledResult(int resultCode) {
        if (resultCode == Activity.RESULT_CANCELED) {
            App.socket().sendChatEvent(bookingId, ChatEvent.PAYMENT_END);
            if (adapter != null)
                adapter.updateChatStatus(ChatEvent.PAYMENT_END);
            else {
                requireActivity().onBackPressed();
                return false;
            }
            hideProgressWindow();
            paymentParameters = null;
            return false;
        }
        return true;
    }

    private void sendPayment(Intent data) {
        final TokenizationResult result = Checkout.createTokenizationResult(data);


        final YooKassaRequest request = YooKassaRequest.newBuilder()
                .bookingId(bookingId)
                .description(paymentParameters.getTitle() + " " + paymentParameters.getSubtitle())
                .paymentToken(result.getPaymentToken())
                .build();
        App.socket().createYaKassaToken(request, new ResponseAdapter(getActivity()) {
            @Override
            public void onSuccess(Api apiName, String json) {

                PaymentResponse response = PaymentResponse.get(json);
                if (!TextUtils.isEmpty(response.getUrl())) {
                    Intent intent = Checkout.create3dsIntent(
                            requireContext(),
                            response.getUrl()
                    );
                    startActivityForResult(intent, REQUEST_CODE_3D_SECURE);
                }

            }

            @Override
            public void onResponse(Api api) {
                hideProgressWindow();
            }

            @Override
            public void failAction() {
                App.socket().sendChatEvent(bookingId, ChatEvent.PAYMENT_END);
                adapter.updateChatStatus(ChatEvent.PAYMENT_END);

            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {

            case REQUEST_CODE_END_CALL:
                runIfAlive(() -> {
                    fabCall.setEnabled(true);
                    if (buttonAttach.getTag().equals(CLOSED) && buttonAttach.isEnabled())
                        buttonAttach.performClick();
                });
                break;
            case REQUEST_CODE_TOKENIZE:
                if (!handleCanceledResult(resultCode))
                    return;
                sendPayment(data);
                break;
            case REQUEST_CODE_3D_SECURE:
                if (!handleCanceledResult(resultCode))
                    return;
                if (resultCode == Checkout.RESULT_ERROR) {
                    if (data.getStringExtra(Checkout.EXTRA_ERROR_DESCRIPTION) != null) {
                        runIfAlive(() -> {
                            showErrorToast(data.getStringExtra(Checkout.EXTRA_ERROR_DESCRIPTION));
                            handleCanceledResult(Activity.RESULT_CANCELED);
                        });
                    }
                    return;
                }

        }


        super.onActivityResult(requestCode, resultCode, data);
    }


    private void detailsLayoutToggle(boolean isExpand) {
        isFirstWasVisible = isExpand;
        isDetailsCollapsed = !isExpand;
        updateConstraints(isExpand ? R.layout.chat_booking_details_expanded : R.layout.chat_booking_details_collapsed);
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) messagesView.getLayoutParams();
        params.topMargin = dpToPx(isExpand ? 24 : -16);
        messagesView.setLayoutParams(params);
        if (isExpand) {
            fabLayout.animate().translationX(0).translationY(0);
            fabCall.animate().scaleY(1.0f).scaleX(1.0f);
        } else {
            fabLayout.animate().translationY(dpToPx(-24)).translationX(dpToPx(8));
            fabCall.animate().scaleX(0.6f).scaleY(0.6f);
        }

    }


    private void updateConstraints(@LayoutRes int layout) {
        if (getContext() == null)
            return;
        ConstraintSet newConstraints = new ConstraintSet();
        if (getContext() == null)
            return;
        newConstraints.clone(getContext(), layout);
        if (getContext() == null)
            return;
        newConstraints.applyTo(detailsLayout);
        if (getContext() == null)
            return;
        TransitionManager.beginDelayedTransition(detailsLayout);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);
        ButterKnife.bind(this, view);


        initToolbar(toolbar, title);
        initHomeButton();
        disableBillButton();
        chatStatus = Preferences.getChatEvent(bookingId);

        ((LinearLayoutManager) Objects.requireNonNull(messagesView.getLayoutManager())).setSmoothScrollbarEnabled(true);
        getMessages();
        carTitleView.setText(carTitle);
        buttonAttach.setTag(CLOSED);
        if (isLocked) {
            buttonAttach.setEnabled(false);
            buttonAttach.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_more_gray));
        }

        messagesView.addOnScrollListener(scrollListener);
        fabCall.setOnClickListener(v ->
                runIfAlive(() -> {
                    if (getArguments() != null && chatMessages != null) {
                        fabCall.setEnabled(false);
                        startActivityForResult(CallActivity.getIntent(getActivity(), chatMessages.getData(),
                                bookingId, getArguments().getString(ARG_PC_URL), CallAction.INITIATE_CALL), REQUEST_CODE_END_CALL);
                    }
                }));
        return view;
    }

    private void setOnlineStatus() {
        final Boolean isOnline = chatMessages.getData().getOnline();
        carTitleView.setText(UiUtils.appendOnlineDot(isOnline, chatMessages.getData().getCarTitle()));
    }

    private static final String DIALOG_BILL = "DialogBill";

    private void getMessages() {
        showProgress();
        ChatApiWrapper.getMessages(getActivity(), bookingId, data -> {

            this.chatMessages = data;
            isPaid = chatMessages.getData().getBookingStatus() == BookingStatus.PAID;

            if (!isLocked && !isPaid) {
                enableBillButton();
            }

            initEventManager();

            isClient = chatMessages.getData().getUserRole() == ChatMessages.BookingRole.CLIENT;

            if (!isClient) {
                buttonRequestBill.setVisibility(View.VISIBLE);
            }

            getBookingDetails();

            List<Message> messages = chatMessages.getMessages();
            boolean isEmpty = messages == null || messages.isEmpty();

            if (isEmpty)
                return;

            if (adapter == null) {
                adapter = new ChatAdapter(AdapterParams.Builder(getActivity())
                        .items(messages)
                        .chatData(chatMessages.getData())
                        .isLocked(isLocked)
                        .callbacks(this)
                        .chatEvent(chatStatus)
                        .build());
            } else {
                adapter.setList(messages);
            }

            messagesView.setAdapter(adapter);

            LinearLayoutManager layoutManager = (LinearLayoutManager) messagesView.getLayoutManager();
            new Handler().postDelayed(() -> {
                if (Objects.requireNonNull(layoutManager).findFirstCompletelyVisibleItemPosition() != 0) {
                    detailsLayoutToggle(false);
                    int index = adapter.getItemCount() - 1;
                    for (Message message : messages) {
                        if (!message.isRead()) {
                            index = messages.indexOf(message);
                            break;
                        }
                    }

                    int finalIndex = index;
                    messagesView.postDelayed(() -> messagesView.smoothScrollToPosition(finalIndex), 100);
                }
                new Handler().postDelayed(() -> App.socket().messagesSetRead(bookingId), DELAY_MILLIS);
            }, 300);

            setOnlineStatus();

            runIfAlive(this::showHints);

        }, this::hideProgress);

    }

    @OnClick(R.id.button_request_bill)
    void onBillRequestClick(View v) {

        if (chatMessages.getData().isPartnerTrusted()) {

            v.setEnabled(false);
            App.socket().sendChatEvent(bookingId, ChatEvent.BILLED_START);
            runIfAlive(() -> {
                FragmentManager fragmentManager = getFragmentManager();
                BillDialog dialog = BillDialog.newInstance(chatMessages.getData());
                dialog.setTargetFragment(ChatFragment.this, 10);
                if (fragmentManager != null) {
                    dialog.show(fragmentManager, DIALOG_BILL);
                }
                new Handler().postDelayed(() -> v.setEnabled(true), 1000);
            });
        } else {
            buttonNextHint.setText(R.string.hint_got_it);
            buttonNextHint.setOnClickListener(closeListener);
            buttonNextHint.setVisibility(View.VISIBLE);
            buttonCloseHint.setOnClickListener(closeListener);
            hintTextView.setText(R.string.b_hint_partner_not_trusted);
            hintsStepsLayout.setVisibility(View.INVISIBLE);
            moreImageFrame.setVisibility(View.INVISIBLE);
            billImageHintFrame.setVisibility(View.VISIBLE);
            hintsLayout.setVisibility(View.VISIBLE);
        }
    }


    private void initEventManager() {
        eventsManager = new ChatEventsManager(signal, chatMessages.getData());
        IntentFilter intentFilter = new IntentFilter();
        for (ChatEvent event : ChatEvent.values()) {
            intentFilter.addAction(Broadcast.ACTION_PREFIX + event.name());
        }
        if (getContext() != null)
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(eventsManager, intentFilter);
    }


    private void showHints() {
        final boolean isTrusted = chatMessages.getData().isPartnerTrusted();
        if (isClient || isTrusted && !Preferences.isFirstEnterToChat(bookingId) || !isTrusted) {
            return;
        }
        hintsLayout.setVisibility(View.VISIBLE);
        buttonCloseHint.setOnClickListener(closeListener);


        buttonNextHint.setOnClickListener(v -> runIfAlive(() -> {

            moreImageFrame.setVisibility(View.INVISIBLE);
            billImageHintFrame.setVisibility(View.VISIBLE);
            hintStep1.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.dot_white_outline));
            hintStep2.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.dot_white));
            hintTextView.setText(R.string.b_hint_partner_trusted);
            buttonNextHint.setText(R.string.hint_got_it);
            buttonNextHint.setOnClickListener(closeListener);

        }));

        for (View view : Arrays.asList(hintsStepsLayout, buttonNextHint))
            view.setVisibility(View.VISIBLE);


    }

    public interface Callbacks {
        void onClickButtonDetails(Long bookingId, ChatMessages.BookingRole bookingRole, @IdRes int container);

        void onClickUploadDocs(BookingData bookingData, @IdRes int container);

    }


    @Override
    protected boolean onOtherMenuItemSelected(MenuItem item) {
        return false;
    }

    @Override
    protected void doOtherBackPressStuff() {
        runIfAlive(this::hideKeys);
    }

    private void disableBillButton() {
        if (getContext() != null) {
            buttonRequestBill.setEnabled(false);
            buttonRequestBill.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_bill_gray));
        }
    }

    private void enableBillButton() {
        if (getContext() != null) {
            buttonRequestBill.setEnabled(true);
            buttonRequestBill.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_bill));
        }
    }

    private void cancelPayment() {

    }

    @Override
    public void onPause() {
//        if (paymentParameters == null)
        App.socket().sendChatEvent(bookingId, ChatEvent.LEAVE_CHAT);
        super.onPause();
    }

    @Override
    public void onStop() {
        App.socket().sendChatEvent(bookingId, ChatEvent.LEAVE_CHAT);
        super.onStop();
    }

    @BindView(R.id.chat_parent_layout)
    FrameLayout chatParentLayout;
    @BindView(R.id.main_content)
    CoordinatorLayout mainContent;
    @BindView(R.id.fab_layout)
    FrameLayout fabLayout;
    @BindView(R.id.fab_call)
    FloatingActionButton fabCall;
    @BindView(R.id.progress_bar)
    ProgressBar progressBar;
    @BindView(R.id.toolbar_layout)
    AppBarLayout toolbarLayout;
    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.details_layout)
    ConstraintLayout detailsLayout;
    @BindView(R.id.order_photo)
    ImageView orderPhoto;
    @BindView(R.id.car_title)
    TextView carTitleView;
    @BindView(R.id.period)
    TextView periodView;
    @BindView(R.id.status)
    TextView statusView;
    @BindView(R.id.button_details)
    Button buttonDetails;
    @BindView(R.id.messages)
    KeyboardDismissingRecyclerView messagesView;
    @BindView(R.id.chat_layout)
    LinearLayout chatLayout;
    @BindView(R.id.button_attach)
    ImageButton buttonAttach;
    @BindView(R.id.button_request_bill)
    ImageButton buttonRequestBill;
    @BindView(R.id.button_message)
    Button buttonMessage;
    @BindView(R.id.text_message)
    AppCompatEditText textMessage;
    @BindView(R.id.button_send)
    ImageButton buttonSend;
    @BindView(R.id.button_docs)
    Button buttonDocs;
    @BindView(R.id.button_cancel_book)
    Button buttonCancelBook;
    //HINTS
    @BindView(R.id.layout_hints)
    FrameLayout hintsLayout;
    @BindView(R.id.button_close_hint)
    ImageButton buttonCloseHint;
    @BindView(R.id.hint_text)
    TextView hintTextView;
    @BindView(R.id.hints_steps_layout)
    LinearLayout hintsStepsLayout;
    @BindView(R.id.hint_step_1)
    ImageView hintStep1;
    @BindView(R.id.hint_step_2)
    ImageView hintStep2;
    @BindView(R.id.more_image_hint_frame)
    FrameLayout moreImageFrame;
    @BindView(R.id.bill_image_hint_frame)
    FrameLayout billImageHintFrame;
    @BindView(R.id.button_next_hint)
    Button buttonNextHint;


}
