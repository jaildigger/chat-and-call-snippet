package rent.auto.chats;

import android.app.Activity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import rent.auto.App;
import rent.auto.model.BookingData;
import rent.auto.model.ChatMessages;
import rent.auto.model.Message;
import rent.auto.socket.Api;
import rent.auto.socket.ResponseAdapter;
import rent.auto.util.UiUtils;

class ChatApiWrapper {

    private static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    static void docsBookingCall(Activity activity, Long bookingId, DocsCall docsCall) {
        App.socket().docsBookingGet(bookingId, new ResponseAdapter(activity) {
            @Override
            public void onSuccess(Api apiName, String json) {
                JsonObject j = App.get().getGson().fromJson(json, JsonObject.class);
                JsonArray docs = App.get().getGson().fromJson(j.get("document_list"), JsonArray.class);
                UiUtils.runIfActivityAlive(activity, () -> docsCall.job(docs));
            }
        });

    }

    static void getBookingDetails(Activity activity, Long bookingId, boolean isClient, BooksCall job) {

        ResponseAdapter adapter = new ResponseAdapter(activity) {
            @Override
            public void onSuccess(Api apiName, String json) {
                UiUtils.runIfActivityAlive(activity, () -> job.job(BookingData.get(json)));
            }
        };

        if (isClient) {
            App.socket().bookingGet(bookingId, adapter);
        } else {
            App.socket().bookingOwnerGet(bookingId, adapter);
        }

    }

    static void sendMessage(Activity activity, Long bookingId, String text, SendMessageCall job) {

        App.socket().messageSend(bookingId, text, new ResponseAdapter(activity) {
            @Override
            public void onSuccess(Api apiName, String json) {

//                Message message = new Message();
//                message.setAuthorId(-1);
//                message.setText(text);
//                message.setMy(true);
//                message.setDeliveredByServer(false);
//                message.setCreateUnix(DateTime.now().getMillis());
                UiUtils.runIfActivityAlive(activity, () -> job.job(null));
            }
        });
    }

    static void getMessages(Activity activity, Long bookingId, MessCall job, Runnable onResponse) {

        App.socket().messagesGet(bookingId, new ResponseAdapter(activity) {
            @Override
            public void onSuccess(Api apiName, String json) {
                UiUtils.runIfActivityAlive(activity, () -> job.job(ChatMessages.get(json)));
            }

            @Override
            public void onResponse(Api api) {
                UiUtils.runIfActivityAlive(activity, onResponse);
            }
        });
    }

    interface DocsCall {
        void job(JsonArray docs);
    }


    interface BooksCall {
        void job(BookingData data);
    }

    interface SendMessageCall {
        void job(Message message);
    }

    interface MessCall {
        void job(ChatMessages chatMessages);
    }


    static void cancelBooking(Activity activity, Long bookingId, boolean isClient, Runnable job, Runnable onResponse) {
        final ResponseAdapter adapter = new ResponseAdapter(activity) {
            @Override
            public void onSuccess(Api apiName, String json) {
                UiUtils.runIfActivityAlive(activity, job);
            }

            @Override
            public void onResponse(Api api) {
                UiUtils.runIfActivityAlive(activity, onResponse);
            }
        };

        if (isClient) {
            App.socket().bookingCancelClient(bookingId, adapter);
        } else {
            App.socket().bookingCancelPartner(bookingId, adapter);
        }
    }
}
