package rent.auto.chats;

import android.app.Activity;

import java.util.List;

import rent.auto.model.ChatData;
import rent.auto.model.Message;
import rent.auto.model.constant.ChatEvent;

public class AdapterParams {
    private List<Message> items;
    private Activity context;
    private ChatData chatData;
    private boolean isLocked;
    private ChatAdapter.PaymentCallback paymentCallback;
    private ChatEvent chatEvent;

    public static Builder Builder(Activity context) {
        return new AdapterParams().new Builder(context);
    }

    public List<Message> getItems() {
        return items;
    }

    public Activity getContext() {
        return context;
    }

    public ChatEvent getChatEvent() {
        return chatEvent;
    }

    ChatAdapter.PaymentCallback getPaymentCallback() {
        return paymentCallback;
    }

    boolean isLocked() {
        return isLocked;
    }

    ChatData getChatData() {
        return chatData;
    }

    public class Builder {

        private Builder(Activity context) {
            AdapterParams.this.context = context;
        }

        public Builder items(List<Message> items) {
            AdapterParams.this.items = items;
            return this;
        }

        Builder chatData(ChatData chatData) {
            AdapterParams.this.chatData = chatData;
            return this;
        }

        public Builder callbacks(ChatAdapter.PaymentCallback callbacks) {
            AdapterParams.this.paymentCallback = callbacks;
            return this;
        }


        Builder isLocked(boolean isLocked) {
            AdapterParams.this.isLocked = isLocked;
            return this;
        }

        public Builder chatEvent(ChatEvent chatEvent) {
            AdapterParams.this.chatEvent = chatEvent;
            return this;
        }

        public AdapterParams build() {
            return AdapterParams.this;
        }
    }
}