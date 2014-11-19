package de.xikolo.controller;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.astuetz.PagerSlidingTabStrip;

import java.util.ArrayList;
import java.util.List;

import de.xikolo.R;
import de.xikolo.controller.exceptions.WrongParameterException;
import de.xikolo.controller.module.AssignmentFragment;
import de.xikolo.controller.module.LtiFragment;
import de.xikolo.controller.module.PagerFragment;
import de.xikolo.controller.module.TextFragment;
import de.xikolo.controller.module.VideoFragment;
import de.xikolo.entities.Course;
import de.xikolo.entities.Item;
import de.xikolo.entities.Module;
import de.xikolo.model.ItemModel;
import de.xikolo.util.DateUtil;
import de.xikolo.util.NetworkUtil;

public class ModuleActivity extends BaseActivity {

    public static final String TAG = ModuleActivity.class.getSimpleName();

    public static final String ARG_COURSE = "arg_course";
    public static final String ARG_MODULE = "arg_module";
    public static final String ARG_ITEM = "arg_item";

    private Course mCourse;
    private Module mModule;
    private Item mItem;

    private ItemModel mItemModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_module);
        setupActionBar();

        Bundle b = getIntent().getExtras();
        if (b == null || !b.containsKey(ARG_COURSE) || !b.containsKey(ARG_MODULE)) {
            throw new WrongParameterException();
        } else {
            this.mCourse = b.getParcelable(ARG_COURSE);
            this.mModule = b.getParcelable(ARG_MODULE);
            this.mItem = b.getParcelable(ARG_ITEM);
        }

        mItemModel = new ItemModel(this, jobManager);

        setTitle(mModule.name);

        // Initialize the ViewPager and set an adapter
        ViewPager pager = (ViewPager) findViewById(R.id.pager);
        ModulePagerAdapter adapter = new ModulePagerAdapter(getSupportFragmentManager(), this, pager, mModule.items);
        pager.setAdapter(adapter);

        // Bind the tabs to the ViewPager
        PagerSlidingTabStrip tabs = (PagerSlidingTabStrip) findViewById(R.id.tabs);
        tabs.setViewPager(pager);

        tabs.setOnPageChangeListener(adapter);

        if (mItem != null) {
            pager.setCurrentItem(mModule.items.indexOf(mItem), false);
            if (mModule.items.indexOf(mItem) == 0) {
                if (NetworkUtil.isOnline(this)) {
                    mItemModel.updateProgression(mModule.items.get(0).id);
                }
            }
        } else {
            if (NetworkUtil.isOnline(this)) {
                mItemModel.updateProgression(mModule.items.get(0).id);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.module, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar module clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
//        int id = item.getItemId();
//        if (id == R.id.action_settings) {
//            return true;
//        }
        return super.onOptionsItemSelected(item);
    }

    public class ModulePagerAdapter extends FragmentPagerAdapter implements ViewPager.OnPageChangeListener, PagerSlidingTabStrip.CustomTabProvider {

        private List<Item> mItems;

        private Context mContext;

        private FragmentManager mFragmentManager;

        private ViewPager mPager;

        private int lastPosition = 0;

        private static final float OPAQUE = 1.0f;
        private static final float HALF_TRANSP = 0.5f;

        private float tabTextAlpha = HALF_TRANSP;
        private float tabTextSelectedAlpha = OPAQUE;

        public ModulePagerAdapter(FragmentManager fm, Context context, ViewPager pager, List<Item> items) {
            super(fm);
            mItems = items;
            mPager = pager;

            // TODO enable when API is working correct
            List<Item> toRemove = new ArrayList<Item>();
            for(Item item : items){
                if(!DateUtil.nowIsBetween(item.available_from, item.available_to)) {
                    toRemove.add(item);
                }
            }
            mItems.removeAll(toRemove);

            mContext = context;
            mFragmentManager = fm;
        }

        @Override
        public View getCustomTabView(ViewGroup viewGroup, int position) {
            View layout = getLayoutInflater().inflate(R.layout.tab_item, null);

            TextView label = (TextView) layout.findViewById(R.id.tabLabel);
            View unseenIndicator = layout.findViewById(R.id.unseenIndicator);

            float alpha = mPager.getCurrentItem() == position ? tabTextSelectedAlpha : tabTextAlpha;
            ViewCompat.setAlpha(label, alpha);
            ViewCompat.setAlpha(unseenIndicator, alpha);

            Item item = mItems.get(position);
            if (!item.progress.visited) {
                unseenIndicator.setVisibility(View.VISIBLE);
            } else {
                unseenIndicator.setVisibility(View.GONE);
            }

            label.setText(getPageTitle(position));

            return layout;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Item item = mItems.get(position);
            String title = "";
            if (item.type.equals(Item.TYPE_TEXT)) {
                title = mContext.getString(R.string.icon_text);
            } else if (item.type.equals(Item.TYPE_VIDEO)) {
                title = mContext.getString(R.string.icon_video);
            } else if (item.type.equals(Item.TYPE_SELFTEST)) {
                title = mContext.getString(R.string.icon_selftest);
            } else if (item.type.equals(Item.TYPE_ASSIGNMENT) || item.type.equals(Item.TYPE_EXAM)) {
                title = mContext.getString(R.string.icon_assignment);
            } else if (item.type.equals(Item.TYPE_LTI)) {
                title = mContext.getString(R.string.icon_lti);
            }
            return title;
        }

        @Override
        public int getCount() {
            return this.mItems.size();
        }

        @Override
        public Fragment getItem(int position) {
            Item item = mItems.get(position);

            // Check if this Fragment already exists.
            // Fragment Name is saved by FragmentPagerAdapter implementation.
            String name = makeFragmentName(R.id.pager, position);
            Fragment fragment = mFragmentManager.findFragmentByTag(name);
            if (fragment == null) {
                if (item.type.equals(Item.TYPE_TEXT)) {
                    fragment = TextFragment.newInstance(mCourse, mModule, mItems.get(position));
                } else if (item.type.equals(Item.TYPE_VIDEO)) {
                    fragment = VideoFragment.newInstance(mCourse, mModule, mItems.get(position));
                } else if (item.type.equals(Item.TYPE_SELFTEST)
                        || item.type.equals(Item.TYPE_ASSIGNMENT)
                        || item.type.equals(Item.TYPE_EXAM)) {
                    fragment = AssignmentFragment.newInstance(mCourse, mModule, mItems.get(position));
                } else if (item.type.equals(Item.TYPE_LTI)) {
                    fragment = LtiFragment.newInstance(mCourse, mModule, mItems.get(position));
                }
            }
            return fragment;
        }

        private String makeFragmentName(int viewId, int index) {
            return "android:switcher:" + viewId + ":" + index;
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            notifyDataSetChanged();

            if (NetworkUtil.isOnline(mContext)) {
                mItemModel.updateProgression(mItems.get(position).id);
            }

            if (lastPosition != position) {
                PagerFragment fragment = (PagerFragment) getItem(lastPosition);
                fragment.pageChanged();
                lastPosition = position;
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            PagerFragment fragment = (PagerFragment) getItem(lastPosition);
            fragment.pageScrolling(state);
        }

    }

}
