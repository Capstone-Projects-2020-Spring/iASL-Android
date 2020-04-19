package org.tensorflow.lite.examples.detection.Adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.rengwuxian.materialedittext.MaterialEditText;

import org.tensorflow.lite.examples.detection.ChatWindow;
import org.tensorflow.lite.examples.detection.R;
import org.tensorflow.lite.examples.detection.model.Chat;
import org.tensorflow.lite.examples.detection.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * The type Message adapter.
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {
    private static  final int MSG_TYPE_LEFT = 0;
    private static  final int MSG_TYPE_RIGHT = 1;


    private Context mContext;
    private List<Chat> mChats;

    /**
     * The Firebase user.
     */
    FirebaseUser firebaseUser;

    /**
     * Instantiates a new Message adapter.
     *
     * @param mContext the m context
     * @param mChats   the m chats
     */
    public MessageAdapter(Context mContext, List<Chat> mChats){
        this.mChats = mChats;
        this.mContext = mContext;
    }

    /**
     * On create view holder message adapter . view holder.
     *
     * @param parent   the parent
     * @param viewType the view type
     * @return the message adapter . view holder
     */
    @NonNull
    @Override
    public MessageAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == MSG_TYPE_RIGHT) {
            View view = LayoutInflater.from(mContext).inflate(R.layout.chat_item_right, parent, false);
            return new MessageAdapter.ViewHolder(view);
        } else {
            View view = LayoutInflater.from(mContext).inflate(R.layout.chat_item_left, parent, false);
            return new MessageAdapter.ViewHolder(view);
        }
    }

    /**
     * On bind view holder.
     *
     * @param holder   the holder
     * @param position the position
     */
    @Override
    public void onBindViewHolder(@NonNull MessageAdapter.ViewHolder holder, int position) {
        Chat chat = mChats.get(position);
        holder.show_message.setText(chat.getText());

    }

    /**
     * Gets item count.
     *
     * @return the item count
     */
    @Override
    public int getItemCount() {
        return mChats.size();
    }

    /**
     * The type View holder.
     */
    public class ViewHolder extends RecyclerView.ViewHolder{
        /**
         * The Show message.
         */
        public TextView show_message;

        /**
         * Instantiates a new View holder.
         *
         * @param itemView the item view
         */
        public ViewHolder(View itemView){
            super(itemView);
            show_message = itemView.findViewById(R.id.show_message);
        }

    }

    /**
     * Gets item view type.
     *
     * @param position the position
     * @return the item view type
     */
    @Override
    public int getItemViewType(int position) {

        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (mChats.get(position).getSenderId().equals(firebaseUser.getUid())){
            return MSG_TYPE_RIGHT;
        } else {
            return MSG_TYPE_LEFT;
        }
    }
}

