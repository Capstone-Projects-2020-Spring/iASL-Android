package org.tensorflow.lite.examples.detection;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.FrameLayout;

import org.tensorflow.lite.examples.detection.fragment.NoteListFragment;

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

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }
}
