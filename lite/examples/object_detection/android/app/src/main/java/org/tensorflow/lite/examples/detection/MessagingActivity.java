package org.tensorflow.lite.examples.detection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.tensorflow.lite.examples.detection.fragment.ChatFragment;
import org.tensorflow.lite.examples.detection.fragment.UserFragment;
import org.tensorflow.lite.examples.detection.model.User;

import java.lang.reflect.Array;
import java.util.ArrayList;

/**
 * MessagingActiviy displays two fragments, including chatfragment and userfragment.
 * The chat fragment displays a list of usernames that the user has sent or received a message to or from.
 * The userfragment displays a list of all the usernames on Firebase Database.
 */
public class MessagingActivity extends AppCompatActivity {

    TextView username;

    FirebaseUser firebaseUser;
    DatabaseReference reference;


    /**
     * Initiates Views and Fragments
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messaging);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("");


        username = findViewById(R.id.username);

        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        reference = FirebaseDatabase.getInstance().getReference("users").child(firebaseUser.getUid());

        reference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                User user = dataSnapshot.getValue(User.class);
                username.setText(user.getName());

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        ViewPager viewPager = findViewById(R.id.viewpager);

        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager());
        viewPagerAdapter.addFragment(new ChatFragment(), "Chats");
        viewPagerAdapter.addFragment(new UserFragment(), "Users");

        viewPager.setAdapter(viewPagerAdapter);
        tabLayout.setupWithViewPager(viewPager);

    }

    /**
     * To specify the options menu for the activity. In this method, it inflates menu resource (defined in XML)
     * into the Menu provided in the callback
     * @param menu
     * @return true if a menu resource found, false otherwise.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    /**
     * Perform an action that is chosen by the user from the menu
     * @param item the action chosen by the user
     * @return true if an action is performed/ defined, false otherwise
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.logout:
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(MessagingActivity.this, StartActivity.class));
                finish();
                return true;
        }

        return false;
    }

    /**
     * This class acts as a fragment factory for the ViewPager
     */
    class ViewPagerAdapter extends FragmentPagerAdapter{

        private ArrayList<Fragment> fragments;
        private ArrayList<String> titles;

        /**
         * Constructor of ViewPagerAdapter
         * @param fm FragmentManager
         */
        ViewPagerAdapter(FragmentManager fm){
            super(fm);
            this.fragments = new ArrayList<>();
            this.titles = new ArrayList<>();
        }

        /**
         * Get a fragment instance at position-index in ArrayList<Fragment>.
         * @param position The position of the fragment in the ArrayList
         * @return the fragment instance or null if cannot find.
         */
        @Override
        public Fragment getItem(int position) {
            return fragments.get(position);
        }

        /**
         * Get the total amount of fragments in the ArrayList
         * @return An Integer, the size of the list
         */
        @Override
        public int getCount() {
            return fragments.size();
        }

        /**
         * Add new fragment instance to the ArrayList
         * @param fragment the fragment
         * @param title the fragment's name or title
         */
        public void addFragment(Fragment fragment, String title){
            fragments.add(fragment);
            titles.add(title);
        }

        /**
         * Get the fragment's title
         * @param position The position of the fragment in the ArrayList
         * @return A String, the fragment's title at position in the list
         */
        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return titles.get(position);
        }
    }
}
