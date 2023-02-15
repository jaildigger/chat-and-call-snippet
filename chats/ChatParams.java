package rent.auto.chats;

import androidx.annotation.IdRes;

public class ChatParams {
    private Long bookingId;
    private String title;
    private String carTitle;
    private boolean isLocked;
    private String pictureUrl;
    @IdRes
    private int containerId = 0;

    String getPictureUrl() {
        return pictureUrl;
    }

    public static Builder Builder() {
        return new ChatParams().new Builder();
    }

    public Long getBookingId() {
        return bookingId;
    }

    public String getTitle() {
        return title;
    }

    public String getCarTitle() {
        return carTitle;
    }

    boolean isLocked() {
        return isLocked;
    }

    public class Builder {

        private Builder() {
        }

        public Builder bookingId(Long bookingId) {
            ChatParams.this.bookingId = bookingId;
            return this;
        }

        public Builder title(String title) {
            ChatParams.this.title = title;
            return this;
        }

        public Builder carTitle(String carTitle) {
            ChatParams.this.carTitle = carTitle;
            return this;
        }

        public Builder isLocked(boolean isLocked) {
            ChatParams.this.isLocked = isLocked;
            return this;
        }

        public Builder picture(String pcUrl) {
            ChatParams.this.pictureUrl = pcUrl;
            return this;
        }

        public Builder containerId(@IdRes int containerId) {
            ChatParams.this.containerId = containerId;
            return this;
        }

        public ChatParams build() {
            return ChatParams.this;
        }
    }
}