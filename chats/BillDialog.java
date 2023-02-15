package rent.auto.chats;

import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.fragment.app.DialogFragment;

import com.appeaser.sublimepickerlibrary.datepicker.SelectedDate;

import java.util.Arrays;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import rent.auto.App;
import rent.auto.R;
import rent.auto.model.ChatData;
import rent.auto.model.Currencies;
import rent.auto.model.constant.ChatEvent;
import rent.auto.model.view.SuffixEditText;
import rent.auto.search.FullDatePicker;
import rent.auto.socket.Api;
import rent.auto.socket.ResponseAdapter;
import rent.auto.socket.request.BillRequest;
import rent.auto.util.Broadcast;
import rent.auto.util.Helpers;
import rent.auto.util.UiUtils;

/**
 * Created by jaydee on 10.03.18.
 */

public class BillDialog extends DialogFragment {


    @BindView(R.id.input_pledge)
    SuffixEditText inputPledge;
    @BindView(R.id.input_total)
    SuffixEditText inputTotal;
    @BindView(R.id.button_close)
    ImageButton buttonClose;
    @BindView(R.id.input_period)
    AppCompatEditText inputPeriod;
    @BindView(R.id.button_send)
    Button buttonSend;
    @BindView(R.id.bill_progress)
    ProgressBar progressBar;


    //    private Callback callback;
    private ChatData data;

    public BillDialog() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.BillDialog);
    }

    public static BillDialog newInstance(ChatData chatData) {
        BillDialog dialog = new BillDialog();
        Bundle args = new Bundle();
        args.putSerializable(Broadcast.ARG_CHAT_DATA, chatData);
        dialog.setArguments(args);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_bill, container, false);
        ButterKnife.bind(this, view);

        assert getArguments() != null;
        data = (ChatData) getArguments().getSerializable(Broadcast.ARG_CHAT_DATA);
//        callback = (Callback) getContext();

        updatePeriod();
        String currentCurrency = " " + Currencies.getSignByCode(data.getBookingCurrency());

        inputPledge.setSuffix(currentCurrency);
        inputPledge.setText(data.getBookingPledge().toString());
        inputTotal.setSuffix(currentCurrency);
        inputTotal.setText(data.getBookingPrice().toString());

        buttonClose.setOnClickListener(v -> UiUtils.runIfActivityAlive(getActivity(), this::dismiss));
        inputPeriod.setOnClickListener(v -> UiUtils.runIfActivityAlive(getActivity(), this::showPicker));

        buttonSend.setOnClickListener(v -> {
            UiUtils.runIfActivityAlive(getActivity(), () -> toggleProgress(true));

            if (!validate()) {
                UiUtils.runIfActivityAlive(getActivity(), () -> toggleProgress(false));
                return;
            }

            BillRequest billRequest = BillRequest.newBuilder()
                    .bookingId(data.getBookingId())
                    .startDate(data.getBookingStartDate())
                    .endDate(data.getBookingEndDate())
                    .pledge(inputPledge.getCurrencyInt())
                    .price(inputTotal.getCurrencyInt())
                    .build();
            App.socket().sendBillRequest(billRequest, new ResponseAdapter(getActivity()) {
                @Override
                public void onSuccess(Api apiName, String json) {
                    UiUtils.runIfActivityAlive(getActivity(), () -> dismiss());
                }

                @Override
                public void failAction() {
                    UiUtils.runIfActivityAlive(getActivity(), () -> toggleProgress(false));
                }
            });

        });

        return view;
    }

    private void toggleProgress(boolean show) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Arrays.asList(buttonSend, buttonClose, inputPeriod, inputPledge, inputTotal).forEach(view -> view.setEnabled(!show));
        } else {
            for (View view : Arrays.asList(buttonSend, buttonClose, inputPeriod, inputPledge, inputTotal)) {
                view.setEnabled(!show);
            }
        }
        progressBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }

    private void updatePeriod() {
        inputPeriod.setText(Helpers.formatPeriodFull(Objects.requireNonNull(getContext()), data.getBookingStartDate(), data.getBookingEndDate()));
    }


    public interface Callback {
        void onSend(BillRequest billRequest);
    }


    private FullDatePicker.Callback datePickerCallback = new FullDatePicker.Callback() {
        @Override
        public void onCancelled() {
        }

        @Override
        public void onRangeSet(SelectedDate selectedDate, String startMinutes, String endMinutes) {

            data.setBookingStartDate(selectedDate.getStartDate().getTime());
            data.setBookingEndDate(selectedDate.getEndDate().getTime());

            updatePeriod();

        }
    };

    private boolean validate() {
        return validateEmpty(inputPeriod) && validateEmpty(inputTotal)
                && validateTotal();
    }

    private void showPicker() {

        FullDatePicker fullDatePicker = FullDatePicker.newInstance(data.getBookingStartDate().getTime(), data.getBookingEndDate().getTime());
        fullDatePicker.setCallback(datePickerCallback);
        fullDatePicker.setStyle(DialogFragment.STYLE_NO_TITLE, 0);
        fullDatePicker.show(getChildFragmentManager(), "SUBLIME_PICKER");
    }

    private boolean validateEmpty(AppCompatEditText editText) {

        if (TextUtils.isEmpty(editText.getText())) {
            editText.setError(getString(R.string.error_field_required));
            return false;
        }

        if (editText.getError() != null)
            editText.setError(null);
        return true;
    }

    private boolean validateTotal() {

        if (inputTotal.getCurrencyInt() <= 0) {
            inputTotal.setError(getString(R.string.error_total_more_zero));
            return false;
        }

        if (inputTotal.getError() != null)
            inputTotal.setError(null);
        return true;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        App.socket().sendChatEvent(data.getBookingId(), ChatEvent.BILLED_END);
    }
}
