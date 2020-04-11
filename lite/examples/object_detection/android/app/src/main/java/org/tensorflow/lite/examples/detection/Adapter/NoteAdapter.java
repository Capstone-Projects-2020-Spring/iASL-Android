package org.tensorflow.lite.examples.detection.Adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.tensorflow.lite.examples.detection.NoteTakingActivity;
import org.tensorflow.lite.examples.detection.R;
import org.tensorflow.lite.examples.detection.model.Note;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.ViewHolder> {

    private Context mContext;
    private List<Note> mNotes;
    private SimpleDateFormat formatter = new SimpleDateFormat("MMM-dd-yyyy");

    public NoteAdapter (Context mContext, List<Note> mNotes){
        this.mNotes = mNotes;
        this.mContext = mContext;
    }

    @NonNull
    @Override
    public NoteAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.note_item, parent, false);
        return new NoteAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteAdapter.ViewHolder holder, int position) {
        Note note = mNotes.get(position);
        holder.noteTitle.setText(note.getTitle());
        holder.createdDate.setText((formatter.format(new Date(note.getTimestamp()))));

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(mContext, NoteTakingActivity.class);
                //TODO putextra in intent
                intent.putExtra("title", note.getTitle());
                intent.putExtra("text", note.getText());
                intent.putExtra("noteId", note.getId());
                mContext.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mNotes.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder{
        public TextView noteTitle, createdDate;

        public ViewHolder(View itemView){
            super(itemView);
            noteTitle = itemView.findViewById(R.id.note_title);
            createdDate = itemView.findViewById(R.id.note_timestamp);
        }

    }
}
