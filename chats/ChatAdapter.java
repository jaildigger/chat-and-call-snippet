package rent.auto.chats;

import static rent.auto.chats.ChatAdapter.Type.INCOMING;
import static rent.auto.chats.ChatAdapter.Type.NOTHING;
import static rent.auto.chats.ChatAdapter.Type.OUTGOING;
import static rent.auto.chats.ChatAdapter.Type.SYSTEM;
import static rent.auto.chats.ChatAdapter.Type.SYSTEM_BILL;
import static rent.auto.chats.ChatAdapter.Type.SYSTEM_DOCS;

import android.app.Activity;
import android.os.Build;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import butterknife.BindView;
import butterknife.BindViews;
import butterknife.ButterKnife;
import rent.auto.R;
import rent.auto.model.Bill;
import rent.auto.model.ChatData;
import rent.auto.model.ChatMessages;
import rent.auto.model.DocumentPreview;
import rent.auto.model.Message;
import rent.auto.model.constant.BookingStatus;
import rent.auto.model.constant.ChatEvent;
import rent.auto.model.constant.NotificationType;
import rent.auto.util.GlideApp;
import rent.auto.util.Helpers;
import rent.auto.util.UiUtils;


public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private final Activity context;
    private List<Message> mValues;
    private final Long bookingId;
    private final boolean isLocked;
    //    private boolean docsSent = true;
    private Callbacks callbacks;
    private PaymentCallback paymentCallback;
    private final boolean isClient;
    private ChatData chatData;
    private ChatEvent event = ChatEvent.NOTHING;
    private final CircularProgressDrawable circularProgressDrawable;

    void resetCallbacks() {
        this.callbacks = null;
        this.paymentCallback = null;
    }


    ChatAdapter(AdapterParams params) {
        mValues = params.getItems();
        this.context = params.getContext();
        this.callbacks = (Callbacks) context;
        this.chatData = params.getChatData();
        this.isLocked = params.isLocked();
        this.bookingId = chatData.getBookingId();
        this.paymentCallback = params.getPaymentCallback();
        this.isClient = chatData.getUserRole() == ChatMessages.BookingRole.CLIENT;
        this.event = params.getChatEvent();
        this.circularProgressDrawable = UiUtils.setUpCircularProgressBar(context);
    }

    void setDocsNotSent() {
//        this.docsSent = false;
//        notifyDocs();
    }

    public void setList(List<Message> items) {
        this.mValues = items;
        notifyDataSetChanged();
    }

    void addItem(Message data) {
        if (mValues == null) {
            mValues = new ArrayList<>();
        }
        mValues.add(data);
        if (isBill(data)) {
            updatePastBills();
        }
    }

    int setItem(Message data) {
        if (mValues == null) {
            mValues = new ArrayList<>();
            mValues.add(0, data);
            return 0;
        } else {
            int index = -1;
            for (Message message : mValues)
                if (!message.isDeliveredByServer()) {
                    index = mValues.indexOf(message);
                    break;
                }
            if (index > -1) {
                mValues.set(index, data);
            }
            return index;
        }
    }

    void setDocsSent() {
//        this.docsSent = true;
//        notifyDocs();
    }

    private void notifyDocs() {

        for (Message item : mValues) {
            if (
                    item.getSystemData() != null &&
                            item.getSystemData().getTrigger() != null &&
                            item.getSystemData().getTrigger() == NotificationType.REQUEST_DOCUMENTS) {
                int index = mValues.indexOf(item);
                if (index >= 0) {
                    notifyItemChanged(index);
                }
            }
        }
    }

    public interface Callbacks {
        void onClickWatchDocs(Long bookingId, boolean isCarOwner);

        void onClickSendDocsFromMessage(Long bookingId);

    }

    void updateChatData(ChatData chatData) {
        this.chatData = chatData;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        Type type = Type.values()[viewType];

        assert type != null;
        switch (type) {
            case OUTGOING:
                return new OutgoingHolder(layoutInflater, parent);
            case SYSTEM:
                return new SystemHolder(layoutInflater, parent);
            case SYSTEM_DOCS:
                return new SystemDocsHolder(layoutInflater, parent);
            case SYSTEM_BILL:
                return new BillHolder(layoutInflater, parent);
            case NOTHING:
                return new NothingHolder(layoutInflater, parent);
            case INCOMING:
            default:
                return new IncomingHolder(layoutInflater, parent);
        }
    }

    @Override
    public int getItemViewType(int position) {
        Message message = mValues.get(position);
        if (message.isMy() && !message.getAuthorId().equals(0)) {
            return OUTGOING.ordinal();
        } else if (message.getAuthorId() == null) {
            return NOTHING.ordinal();
        } else if (message.getAuthorId().equals(0)) {
            if (message.getSystemData() != null && message.getSystemData().getTrigger() != null) {
                NotificationType trigger = message.getSystemData().getTrigger();
                if (NotificationType.SEND_DOCUMENTS == trigger) {
                    return SYSTEM_DOCS.ordinal();
                } else if (NotificationType.EVENT_REQUEST_PAYMENT == trigger) {
                    return SYSTEM_BILL.ordinal();
                } else return SYSTEM.ordinal();
            } else {
                return SYSTEM.ordinal();
            }
        } else {
            return INCOMING.ordinal();
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        Message message = mValues.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    private boolean isNotLatestBill(Message message) {
        Message m = findLastBill();
        if (m == null) {
            return false;
        } else {
            return !m.getCreateUnix().equals(message.getCreateUnix());
        }
    }

    private void updatePastBills() {
        Message latest = findLastBill();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mValues.stream().filter(m -> isBill(m) && !m.getCreateUnix().equals(latest.getCreateUnix())).forEach(m -> {
                notifyItemChanged(mValues.indexOf(m));
            });
        } else {
            for (Message m : mValues) {
                if (isBill(m) && !m.getCreateUnix().equals(latest.getCreateUnix())) {
                    notifyItemChanged(mValues.indexOf(m));
                }
            }
        }
    }


    private Message findLastBill() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return mValues.stream().filter(this::notEmptyBill).max(Comparator.comparingLong(Message::getCreateUnix)).orElse(null);
        } else {
            long create = 0L;
            Message last = null;
            for (Message message : mValues) {
                if (notEmptyBill(message) && message.getCreateUnix() > create) {
                    last = message;
                    create = message.getCreateUnix();
                }
            }
            return last;
        }

    }

    private int findLastBillPosition() {
        Message message = findLastBill();
        if (message != null) {
            return mValues.indexOf(message);
        } else {
            return -1;
        }
    }

    private boolean notEmptyBill(Message message) {
        return message.getBill() != null && message.getBill().getStartDate() != null;
    }

    private boolean isBill(Message message) {
        return message.getSystemData() != null && message.getSystemData().getTrigger() != null
                && message.getSystemData().getTrigger() == NotificationType.EVENT_REQUEST_PAYMENT;
    }

    public interface PaymentCallback {
        void onClickPay(Long bookingId, Bill bill);

        void onClickChange();
    }

    boolean updateChatStatus(ChatEvent event) {
        return updateChatStatus(event, null);
    }

    boolean updateChatStatus(ChatEvent event, ChatData chatData) {
        if (!ChatEvent.eventsForMessages().contains(event)) {
            return false;
        }
        if (chatData != null && chatData.getBookingStartDate() != null)
            this.chatData = chatData;
        this.event = event;
        notifyItemChanged(findLastBillPosition());
        return true;
    }

    enum Type {
        INCOMING, OUTGOING, SYSTEM, SYSTEM_DOCS, SYSTEM_BILL, NOTHING
    }


    public abstract class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View itemView) {
            super(itemView);
        }

        public abstract void bind(Message item);
    }


    public class IncomingHolder extends ViewHolder {

        @BindView(R.id.message)
        TextView message;
        @BindView(R.id.timestamp)
        TextView timestamp;

        IncomingHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.item_incoming_message, parent, false));
            ButterKnife.bind(this, itemView);
        }

        public void bind(Message item) {
            String text = item.getText() == null ? item.getContent() : item.getText();
            message.setText(text);
            String timestampText = Helpers.getTimestamp(item.getCreateDate(), item.getCreateTime());
            timestamp.setText(timestampText);

        }


    }

    public class NothingHolder extends ViewHolder {

        NothingHolder(LayoutInflater inflater, ViewGroup parent) {
            super(new View(parent.getContext()));
        }

        public void bind(Message item) {
        }


    }

    public class OutgoingHolder extends ViewHolder {

        @BindView(R.id.message)
        TextView message;
        @BindView(R.id.timestamp)
        TextView timestamp;
        @BindView(R.id.status)
        ImageView status;

        OutgoingHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.item_outgoing_message, parent, false));
            ButterKnife.bind(this, itemView);
        }

        public void bind(Message item) {
            String text = item.getText() == null ? item.getContent() : item.getText();
            message.setText(text);
            String timestampText = Helpers.getTimestamp(item.getCreateDate(), item.getCreateTime());
            timestamp.setText(timestampText);
            GlideApp.with(itemView).load(item.isDeliveredByServer() ? R.drawable.ic_sent : R.drawable.ic_wait).into(status);
        }
    }

    public class SystemHolder extends ViewHolder {

        @BindView(R.id.message)
        HtmlTextView message;
        @BindView(R.id.timestamp)
        TextView timestamp;
        @BindView(R.id.button_send)
        Button buttonSend;

        SystemHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.item_system_plain, parent, false));
            ButterKnife.bind(this, itemView);
        }

        //        @RequiresApi(api = Build.VERSION_CODES.N)
        public void bind(Message item) {
            String mess = item.getContent();
            if (mess == null)
                mess = item.getText();
            if (mess != null)
                message.setHtml(mess);
            String timestampText = Helpers.getTimestamp(item.getCreateDate(), item.getCreateTime());
            timestamp.setText(timestampText);
            if (
                    item.getSystemData() != null &&
                            item.getSystemData().getTrigger() != null &&
                            item.getSystemData().getTrigger() == NotificationType.REQUEST_DOCUMENTS &&
                            isClient
//                            && !docsSent
            ) {
                buttonSend.setVisibility(View.VISIBLE);
                buttonSend.setEnabled(!isLocked);
                buttonSend.setOnClickListener(v -> callbacks.onClickSendDocsFromMessage(bookingId));
            } else {
                buttonSend.setVisibility(View.GONE);
            }


        }


    }

    public class SystemDocsHolder extends ViewHolder {

        @BindView(R.id.pass1)
        ImageView pass1;
        @BindView(R.id.pass2)
        ImageView pass2;
        @BindView(R.id.license1)
        ImageView license1;
        @BindView(R.id.license2)
        ImageView license2;
        @BindView(R.id.timestamp)
        TextView timestamp;
        @BindView(R.id.message)
        HtmlTextView message;

        SystemDocsHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.item_system_docs, parent, false));
            ButterKnife.bind(this, itemView);

        }


        @Override
        public void bind(Message item) {
            if (item.getDocumentsPreview() == null)
                return;
            for (DocumentPreview dp : item.getDocumentsPreview()) {
                ImageView imageView;
                if (dp.getType() != null)
                    switch (dp.getType()) {
                        case LICENSE:
                            imageView = license1;
                            break;
                        case LICENSE_BACK:
                            imageView = license2;
                            break;
                        case PASSPORT_BACK:
                            imageView = pass2;
                            break;
                        case PASSPORT:
                        default:
                            imageView = pass1;
                    }
                else imageView = pass1;
                byte[] imageByteArray = Base64.decode(dp.getData(), Base64.DEFAULT);
                GlideApp.with(itemView).asBitmap().placeholder(circularProgressDrawable).load(imageByteArray).into(imageView);
            }
            message.setHtml(item.getText() == null ? item.getContent() : item.getText());
            String timestampText = Helpers.getTimestamp(item.getCreateDate(), item.getCreateTime());
            timestamp.setText(timestampText);
            itemView.setOnClickListener(v -> callbacks.onClickWatchDocs(bookingId, !isClient));


        }
    }


    public class BillHolder extends ViewHolder {

        public Message item;

        @BindView(R.id.rent_title)
        TextView rentTitle;
        @BindView(R.id.rent_period)
        TextView rentPeriod;
        @BindView(R.id.rent_pledge)
        TextView rentPledge;
        @BindView(R.id.rent_total)
        TextView rentTotal;
        @BindView(R.id.rent_subtotal)
        TextView rentSubtotal;
        @BindView(R.id.rent_commission)
        TextView rentCommission;
        @BindView(R.id.bill_status_layout)
        LinearLayout billStatusLayout;
        @BindView(R.id.bill_status)
        TextView billStatus;
        @BindView(R.id.button_change_bill)
        Button buttonChangeBill;
        @BindViews({R.id.second_divider, R.id.bill_status_layout})
        List<View> statusViews;
        @BindView(R.id.button_pay)
        Button buttonPay;
        @BindView(R.id.bill_pay_layout)
        LinearLayout billPayLayout;


        BillHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.item_bill_message, parent, false));
            ButterKnife.bind(this, itemView);
        }

        public void bind(Message item) {
            this.item = item;
            final Bill bill = item.getBill();

            if (bill == null)
                return;

            rentTitle.setText(context.getString(R.string.rent_title, chatData.getCarTitle()));
            rentPeriod.setText(Helpers.formatPeriodFull(Objects.requireNonNull(context), bill.getStartDate(), bill.getEndDate()));
            rentPledge.setText(Helpers.formatSuffixPledge(bill.getPledge(), chatData.getBookingCurrencySymbol(), context));
            rentSubtotal.setText(Helpers.formatSuffix(item.getBill().getPrice(), chatData.getBookingCurrencySymbol(), context));
            rentCommission.setText(Helpers.formatSuffix(item.getBill().getCommissionCost(), chatData.getBookingCurrencySymbol(), context));
            rentTotal.setText(Helpers.formatSuffix(item.getBill().getPriceWithCommission(), chatData.getBookingCurrencySymbol(), context));

            boolean isNotLast = isNotLatestBill(item);

            if (isClient) {

                int buttonText = 0;
                if (BookingStatus.CANCELED == chatData.getBookingStatus()) {
                    buttonText = R.string.status_canceled;
                } else if (isNotLast) {
                    buttonText = R.string.b_new_bill;
                } else if (event == ChatEvent.STATUS_PAYED || chatData.getBookingStatus() == BookingStatus.PAID) {
                    buttonText = R.string.bill_paid;
                } else if (event == ChatEvent.BILLED_START) {
                    buttonText = R.string.bill_start;
                } else {
                    buttonPay.setText(context.getString(R.string.b_pay, Helpers.formatMoney(bill.getPriceWithCommission()), chatData.getBookingCurrencySymbol()));
                    buttonPay.setOnClickListener(v -> paymentCallback.onClickPay(bookingId, bill));
                    buttonPay.setEnabled(true);
                }
                if (buttonText > 0) {
                    buttonPay.setText(buttonText);
                    buttonPay.setEnabled(false);
                } else if (event == ChatEvent.PAYMENT_START) {
                    buttonPay.setEnabled(false);
                } else if (event == ChatEvent.PAYMENT_END) {
                    buttonPay.setEnabled(true);
                }
                billPayLayout.setVisibility(View.VISIBLE);


            } else {

                int statusText = 0;

                if (BookingStatus.CANCELED == chatData.getBookingStatus()) {
                    statusText = R.string.status_canceled;
                } else if (isNotLast) {
                    statusText = R.string.b_new_bill;
                } else if (BookingStatus.BILLED == chatData.getBookingStatus() && event != ChatEvent.PAYMENT_START) {
                    statusText = R.string.bill_sent;
                } else if (event == ChatEvent.STATUS_PAYED || BookingStatus.PAID == chatData.getBookingStatus()) {
                    statusText = R.string.bill_paid;
                } else if (event == ChatEvent.PAYMENT_START) {
                    statusText = R.string.b_payment_in_progress;
                }
                if (statusText > 0) {
                    billStatus.setText(statusText);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    statusViews.forEach(view -> view.setVisibility(View.VISIBLE));
                } else {
                    for (View view : statusViews) {
                        view.setVisibility(View.VISIBLE);
                    }
                }

                if (BookingStatus.BILLED == chatData.getBookingStatus() && event != ChatEvent.PAYMENT_START && !isNotLast) {
                    buttonChangeBill.setVisibility(View.VISIBLE);
                    buttonChangeBill.setOnClickListener(v -> paymentCallback.onClickChange());
                } else {
                    buttonChangeBill.setVisibility(View.INVISIBLE);
                }
            }


        }
    }


}
