package org.tensorflow.lite.examples.detection.fragment;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.tensorflow.lite.examples.detection.Adapter.NoteAdapter;
import org.tensorflow.lite.examples.detection.Adapter.UserAdapter;
import org.tensorflow.lite.examples.detection.R;
import org.tensorflow.lite.examples.detection.model.Note;
import org.tensorflow.lite.examples.detection.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * A fragment that display a list of notes that is fetched from Firebase Database
 */
public class NoteListFragment extends Fragment {
    private RecyclerView recyclerView;
    private NoteAdapter noteAdapter;
    private List<Note> mNotes;

    /**
     * Creates Views
     * @param inflater inflater that transform xml to java code
     * @param container the container that contains the notelist fragment
     * @param savedInstanceState
     * @return a view/fragment to the parent's thread/caller's thread
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_note_list, container, false);

        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        mNotes = new ArrayList<>();
        fetchNotes();
        return view;
    }

    /**
     * Connect to the Firebase Database a create a list of note objects. Only get notes
     * that belong to the current user.
     */
    private void fetchNotes(){
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("notes");

        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mNotes.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                    Note note = snapshot.getValue(Note.class);

                    Log.d("here",note.getTitle());
                    if (note.getOwnerId().equals(firebaseUser.getUid())){
                        mNotes.add(note);
                    }
                }
                noteAdapter = new NoteAdapter(getContext(), mNotes);
                recyclerView.setAdapter(noteAdapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }
}
