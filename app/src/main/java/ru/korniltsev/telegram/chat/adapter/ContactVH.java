package ru.korniltsev.telegram.chat.adapter;

import android.view.View;
import android.widget.TextView;
import org.drinkless.td.libcore.telegram.TdApi;
import ru.korniltsev.telegram.chat.R;
import ru.korniltsev.telegram.chat.adapter.view.ForwardedMessageView;
import ru.korniltsev.telegram.chat.debug.CustomCeilLayout;
import ru.korniltsev.telegram.core.rx.items.ChatListItem;
import ru.korniltsev.telegram.core.rx.items.MessageItem;
import ru.korniltsev.telegram.core.views.AvatarView;
import ru.korniltsev.telegram.common.AppUtils;

class ContactVH extends BaseAvatarVH {

    private final TextView text;
    private final TextView message_time;
    private final TextView nick;
    private final AvatarView avatar;

    public ContactVH(CustomCeilLayout itemView, Adapter adapter) {
        super(itemView, adapter);


        ForwardedMessageView contentView = (ForwardedMessageView) adapter.getViewFactory().inflate(R.layout.chat_item_message_forward, root, false);
        contentView.disableBlueMargin();
        root.addContentView(contentView);

        text = ((TextView) contentView.findViewById(R.id.forward_text));
        message_time = ((TextView) contentView.findViewById(R.id.forward_time));
        nick = ((TextView) contentView.findViewById(R.id.forward_nick));
        BaseAvatarVH.colorizeNick(nick);
        avatar = ((AvatarView) contentView.findViewById(R.id.forward_avatar));

        message_time.setVisibility(View.GONE);
        text.setTextColor(0xff777777);

    }

    @Override
    public void bind(ChatListItem item, long lastReadOutbox) {
        super.bind(item, lastReadOutbox);


        TdApi.Message rawMsg = ((MessageItem) item).msg;
        TdApi.MessageContact msg = (TdApi.MessageContact) rawMsg.message;
        this.text.setText(msg.phoneNumber);



        TdApi.User user = adapter.getUserHolder().getUser(msg.userId);
        if (user == null){
            avatar.setStub(msg);
        } else {
            avatar.loadAvatarFor(user);
        }
        nick.setText(
                AppUtils.uiName(msg.firstName, msg.lastName));


    }
}
