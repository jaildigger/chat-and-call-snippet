package rent.auto.chats;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.PictureDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import rent.auto.App;
import rent.auto.AuthResponsibleFragment;
import rent.auto.R;
import rent.auto.model.Chat;
import rent.auto.model.ChatData;
import rent.auto.model.Message;
import rent.auto.model.NewMessage;
import rent.auto.model.constant.BookingStatus;
import rent.auto.socket.Api;
import rent.auto.socket.ResponseAdapter;
import rent.auto.util.GlideApp;
import rent.auto.util.Helpers;
import rent.auto.util.SvgSoftwareLayerSetter;
import rent.auto.util.UiUtils;
import rent.auto.util.WrapContentLinearLayoutManager;

import static rent.auto.util.Broadcast.ACTION_NEW_MESSAGE;


public class ChatsChildFragment extends AuthResponsibleFragment {


    private static final String EXTRA_JSON = "rent.auto.extra.json";
    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.empty_chats_layout)
    LinearLayout emptyLayout;
    @BindView(R.id.chats_recycler_view)
    RecyclerView mRecyclerView;
    @BindView(R.id.progress_bar)
    ProgressBar progressBar;
    private List<Chat> chats;
    private Callbacks mCallbacks;
    private ChatAdapter mAdapter;
    private ChatBadgeCallbacks mBadgeCallbacks;
    private RequestBuilder<PictureDrawable> requestBuilder;
    private final BroadcastReceiver nReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String json = intent.getStringExtra(EXTRA_JSON);
            NewMessage newMessage = NewMessage.get(json);

            if (mAdapter != null) {

                Chat chat = null;
                for (Chat ch : mAdapter.mItems) {
                    if (ch.getTarget().equals(newMessage.getBookingId())) {
                        chat = ch;
                        break;
                    }
                }

                if (chat != null) {
                    chat.setLastMessage(newMessage.getMessage());
                    chat.setData(newMessage.getChatData());
                    chat.setUnreadCount(chat.getUnreadCount() + 1);
                    final boolean isItTheFirstUnread = chat.getUnreadCount() == 1;
                    int finalIndex = mAdapter.setItem(chat);
                    if (mRecyclerView != null)
                        if (mRecyclerView.getAdapter() == null) {
                            mRecyclerView.setAdapter(mAdapter);
                        }
                    runIfAlive(() -> {
                        mAdapter.notifyItemChanged(finalIndex);
                        if (isItTheFirstUnread)
                            mBadgeCallbacks.onChatReceive();
                    });

                } else {
                    runIfAlive(() -> {
                        getChats();
                        mBadgeCallbacks.onChatReceive();
                    });
                }
            } else {
                runIfAlive(() -> {
                    getChats();
                    mBadgeCallbacks.onChatReceive();
                });
            }

        }
    };


    public ChatsChildFragment() {
    }

    public static Intent getIntent(String json) {
        Intent intent = new Intent(ACTION_NEW_MESSAGE);
        intent.putExtra(EXTRA_JSON, json);
        return intent;
    }

    public static ChatsChildFragment newInstance() {
        return new ChatsChildFragment();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
    }

    @Override
    protected void reloadUi() {
        getChats();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mCallbacks = (Callbacks) context;
        mBadgeCallbacks = (ChatBadgeCallbacks) context;
        LocalBroadcastManager.getInstance(Objects.requireNonNull(getContext())).registerReceiver(nReceiver, new IntentFilter(ACTION_NEW_MESSAGE));

    }


    @Override
    public void onDetach() {
        mCallbacks = null;
        mBadgeCallbacks = null;
        App.socket().removeSubscription(Api.NEW_NOTIFICATION);
        LocalBroadcastManager.getInstance(Objects.requireNonNull(getContext())).unregisterReceiver(nReceiver);
        super.onDetach();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestBuilder = GlideApp.with(this)
                .as(PictureDrawable.class)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .listener(new SvgSoftwareLayerSetter());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View result = inflater.inflate(R.layout.fragment_chats, container, false);
        ButterKnife.bind(this, result);
        emptyLayout.setVisibility(View.INVISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        setHasOptionsMenu(true);
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        assert activity != null;
        activity.setSupportActionBar(mToolbar);
        Objects.requireNonNull(activity.getSupportActionBar()).setDisplayShowTitleEnabled(false);
        mRecyclerView.setLayoutManager(new WrapContentLinearLayoutManager(getContext()));
        getChats();

        return result;
    }

    private int countUnreadChats(List<Chat> chats) {
        int c = 0;
        for (Chat chat : chats) {
            if (chat.getUnreadCount() != null && chat.getUnreadCount() > 0) {
                c++;
            }
        }
        return c;
    }

    private void getChats() {

        App.socket().chatsGet(new ResponseAdapter(getActivity()) {
            @Override
            public void onSuccess(Api apiName, String json) {

                chats = Chat.getChats(json);
                boolean flag = chats == null || chats.isEmpty();
                if (chats == null) {
                    manageEmpty(true);
                    return;
                }

                runIfAlive(() -> {
                    if (!flag && mAdapter == null) {
                        mAdapter = new ChatAdapter(chats);
                    } else if (mAdapter != null) {
                        mAdapter.setList(chats);
                    }
                    mBadgeCallbacks.onChatBadge(countUnreadChats(chats));
                    if (mRecyclerView != null) {
                        mRecyclerView.setAdapter(mAdapter);
                        manageEmpty(flag);
                    }
                });
            }

            @Override
            public void responseAction() {
                runIfAlive(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.INVISIBLE);
                });
            }
        });
    }


    private void manageEmpty(boolean isEmpty) {
        emptyLayout.setVisibility(isEmpty ? View.VISIBLE : View.INVISIBLE);
        mRecyclerView.setVisibility(isEmpty ? View.INVISIBLE : View.VISIBLE);

    }


    public interface Callbacks {
        void onClickChat(ChatParams params);

    }

    class ChatHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        @BindView(R.id.chat_photo)
        ImageView mChatPhoto;
        @BindView(R.id.car_title)
        TextView mCarTitle;
        @BindView(R.id.author)
        TextView mAuthor;
        @BindView(R.id.message)
        HtmlTextView mMessage;
        @BindView(R.id.status)
        TextView mStatus;
        @BindView(R.id.timestamp)
        TextView mTime;
        @BindView(R.id.counter)
        TextView mCounter;
        private Chat mItem;


        ChatHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.item_chat, parent, false));
            itemView.setOnClickListener(this);
            ButterKnife.bind(this, itemView);
        }

        public void bind(Chat item) {

            mItem = item;
            String extension = item.getPcUrl().substring(item.getPcUrl().lastIndexOf(".") + 1);
            if (extension.toLowerCase().equals("svg")) {
                requestBuilder.load(item.getPcUrl()).into(mChatPhoto);
            } else {
                GlideApp.with(ChatsChildFragment.this).load(item.getPcUrl())
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true).into(mChatPhoto);
            }
            ChatData data = item.getData();
            Message lastMessage = item.getLastMessage();
            mCarTitle.setText(UiUtils.appendOnlineDot(data.getOnline(), data.getCarTitle()));
            mAuthor.setText(item.getName());
            mStatus.setText(Helpers.visibleStatus(data.getBookingStatus(), data.getBookingEndDate()).getValue());
            if (mItem.getUnreadCount() == null || mItem.getUnreadCount() <= 0) {
                mCounter.setVisibility(View.INVISIBLE);
            } else {
                mCounter.setVisibility(View.VISIBLE);
                mCounter.setText(String.valueOf(mItem.getUnreadCount()));
            }
            if (lastMessage == null)
                return;
            if (lastMessage.getCreateDate() != null) {
                String timestampText = Helpers.getTimestamp(lastMessage.getCreateDate(), lastMessage.getCreateTime());
                mTime.setText(timestampText);
            }
            if (lastMessage.getText() != null && !lastMessage.getText().isEmpty())
                mMessage.setHtml(lastMessage.getText());
            if (lastMessage.getContent() != null && !lastMessage.getContent().isEmpty())
                mMessage.setHtml(lastMessage.getContent());


        }

        @Override
        public void onClick(View view) {
            ChatData data = mItem.getData();
            boolean isLocked = BookingStatus.CANCELED == data.getBookingStatus() ||
                    Helpers.visibleStatus(data.getBookingStatus(), data.getBookingEndDate()) == BookingStatus.COMPLETE;

            ChatParams params = ChatParams.Builder().
                    bookingId(mItem.getTarget()).
                    carTitle(mItem.getData().getCarTitle()).
                    title(mItem.getName()).
                    isLocked(isLocked).
                    picture(mItem.getPcUrl()).build();
            mCallbacks.onClickChat(params);
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatHolder> {

        private List<Chat> mItems;

        ChatAdapter(List<Chat> items) {
            mItems = items;
            setHasStableIds(true);
        }

        public void addItem(Chat data) {
            if (mItems == null) {
                mItems = new ArrayList<>();
            }
            mItems.add(0, data);
        }

        int setItem(Chat data) {
            if (mItems == null) {
                mItems = new ArrayList<>();
                mItems.add(0, data);
                return 0;
            } else {
                int index = mItems.indexOf(data);
                if (index > -1) {
                    mItems.set(index, data);
                }
                return index;
            }
        }

        public void setList(List<Chat> items) {
            this.mItems = items;
            notifyDataSetChanged();
        }


        @Override
        public long getItemId(int position) {
            return mItems.get(position).hashCode();
        }

        @NonNull
        @Override
        public ChatHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            return new ChatHolder(layoutInflater, parent);
        }

        @Override
        public void onBindViewHolder(@NonNull ChatHolder holder, int position) {
            Chat item = mItems.get(position);
            holder.bind(item);

        }

        @Override
        public int getItemCount() {
            return (null != mItems ? mItems.size() : 0);
        }
    }


}
