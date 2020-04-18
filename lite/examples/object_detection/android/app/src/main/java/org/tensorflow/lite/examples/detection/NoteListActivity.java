package org.tensorflow.lite.examples.detection;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.FrameLayout;

import org.tensorflow.lite.examples.detection.fragment.NoteListFragment;


/**
 * This activity shows a list of notes that belong to the user. This activity
 * uses notelist fragment.
 */
public class NoteListActivity extends AppCompatActivity {

    private NoteListFragment noteListFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_list);

        noteListFragment = (NoteListFragment) getSupportFragmentManager().findFragmentByTag("notelistfragment");
        if (noteListFragment != null){
            getSupportFragmentManager().beginTransaction()
                    .remove(noteListFragment)
                    .add(R.id.list_framelayout, new NoteListFragment(), "notelistfragment")
                    .commitAllowingStateLoss();
        } else {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.list_framelayout, new NoteListFragment(), "notelistfragment")
                    .commitAllowingStateLoss();
        }
    }

    /**
     * Remove the activity from the stack
     */
    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }
}
