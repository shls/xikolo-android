package de.xikolo.controller.main;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import de.greenrobot.event.EventBus;
import de.xikolo.GlobalApplication;
import de.xikolo.R;
import de.xikolo.controller.CourseActivity;
import de.xikolo.controller.CourseDetailsActivity;
import de.xikolo.controller.dialogs.ProgressDialog;
import de.xikolo.controller.helper.NotificationController;
import de.xikolo.controller.helper.RefeshLayoutController;
import de.xikolo.controller.main.adapter.CourseListAdapter;
import de.xikolo.controller.navigation.adapter.NavigationAdapter;
import de.xikolo.data.entities.Course;
import de.xikolo.model.CourseModel;
import de.xikolo.model.Result;
import de.xikolo.model.UserModel;
import de.xikolo.model.events.EnrollEvent;
import de.xikolo.model.events.LoginEvent;
import de.xikolo.model.events.LogoutEvent;
import de.xikolo.model.events.UnenrollEvent;
import de.xikolo.util.DateUtil;
import de.xikolo.util.NetworkUtil;
import de.xikolo.util.ToastUtil;
import de.xikolo.view.AutofitRecyclerView;
import de.xikolo.view.SpaceItemDecoration;

public class CourseListFragment extends ContentFragment implements SwipeRefreshLayout.OnRefreshListener,
        CourseListAdapter.OnCourseButtonClickListener {

    public static final String TAG = CourseListFragment.class.getSimpleName();

    public static final String ARG_FILTER = "arg_filter";

    public static final String FILTER_ALL = "filter_all";
    public static final String FILTER_MY = "filter_my";

    private static final String ARG_COURSES = "arg_courses";

    private String filter;
    private SwipeRefreshLayout refreshLayout;

    private AutofitRecyclerView recyclerView;
    private CourseListAdapter courseListAdapter;

    private NotificationController notificationController;

    private List<Course> courses;

    private CourseModel courseModel;

    public CourseListFragment() {
        // Required empty public constructor
    }

    public static CourseListFragment newInstance(String filter) {
        CourseListFragment fragment = new CourseListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_FILTER, filter);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            filter = getArguments().getString(ARG_FILTER);
        }
        if (savedInstanceState != null) {
            courses = savedInstanceState.getParcelableArrayList(ARG_COURSES);
        }
        setHasOptionsMenu(true);

        courseModel = new CourseModel(jobManager);

        EventBus.getDefault().register(this);
    }

    private void requestCourses(final boolean userRequest, final boolean includeProgress) {
        Result<List<Course>> result = new Result<List<Course>>() {
            @Override
            protected void onSuccess(List<Course> result, DataSource dataSource) {
                if (result.size() > 0) {
                    notificationController.setInvisible();
                }
                if (!NetworkUtil.isOnline(getActivity()) && dataSource.equals(DataSource.LOCAL) ||
                        dataSource.equals(DataSource.NETWORK)) {
                    refreshLayout.setRefreshing(false);
                }

                courses = result;

                if (!NetworkUtil.isOnline(getActivity()) && dataSource.equals(DataSource.LOCAL) && result.size() == 0) {
                    notificationController.setTitle(R.string.notification_no_network);
                    notificationController.setSummary(R.string.notification_no_network_with_offline_mode_summary);
                    notificationController.setNotificationVisible(true);
                    refreshLayout.setRefreshing(false);
                    courseListAdapter.clear();
                } else {
                    updateView();
                }
            }

            @Override
            protected void onWarning(WarnCode warnCode) {
                if (warnCode == WarnCode.NO_NETWORK && userRequest) {
                    NetworkUtil.showNoConnectionToast();
                }
            }

            @Override
            protected void onError(ErrorCode errorCode) {
                courses = null;

                if (errorCode == ErrorCode.NO_RESULT) {
                    ToastUtil.show(GlobalApplication.getInstance().getString(R.string.toast_no_courses)
                            + " " + GlobalApplication.getInstance().getString(R.string.toast_no_network));
                } else if (errorCode == ErrorCode.NO_NETWORK && userRequest) {
                    NetworkUtil.showNoConnectionToast();
                }

                updateView();
            }
        };

        if (isMyCoursesFilter() && !UserModel.isLoggedIn(getActivity())) {
            courses = null;

            notificationController.setTitle(R.string.notification_please_login);
            notificationController.setSummary(R.string.notification_please_login_summary);
            notificationController.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    activityCallback.selectDrawerSection(NavigationAdapter.NAV_ID_PROFILE);
                }
            });
            notificationController.setNotificationVisible(true);
            refreshLayout.setRefreshing(false);
        } else {
            if (courses == null || courses.size() == 0) {
                notificationController.setProgressVisible(true);
            } else {
                refreshLayout.setRefreshing(true);
            }
            if (isMyCoursesFilter()) {
                courseModel.getCourses(result, includeProgress, CourseModel.CourseFilter.MY);
            } else {
                courseModel.getCourses(result, includeProgress);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (courses != null) {
            outState.putParcelableArrayList(ARG_COURSES, (ArrayList<Course>) courses);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.fragment_course_list, container, false);

        refreshLayout = (SwipeRefreshLayout) layout.findViewById(R.id.refreshLayout);
        RefeshLayoutController.setup(refreshLayout, this);

        if (isAllCoursesFilter()) {
            courseListAdapter = new CourseListAdapter(this, CourseModel.CourseFilter.ALL);
        } else if (isMyCoursesFilter()) {
            courseListAdapter = new CourseListAdapter(this, CourseModel.CourseFilter.MY);
        }

        recyclerView = (AutofitRecyclerView) layout.findViewById(R.id.recyclerView);
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(courseListAdapter);

        recyclerView.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return courseListAdapter.isHeader(position) ? recyclerView.getSpanCount() : 1;
            }
        });

        recyclerView.addItemDecoration(new SpaceItemDecoration(
                getActivity().getResources().getDimensionPixelSize(R.dimen.card_horizontal_margin),
                getActivity().getResources().getDimensionPixelSize(R.dimen.card_vertical_margin),
                false,
                new SpaceItemDecoration.RecyclerViewInfo() {
                    @Override
                    public boolean isHeader(int position) {
                        return courseListAdapter.isHeader(position);
                    }

                    @Override
                    public int getSpanCount() {
                        return recyclerView.getSpanCount();
                    }

                    @Override
                    public int getItemCount() {
                        return courseListAdapter.getItemCount();
                    }
                }));

        notificationController = new NotificationController(layout);
        notificationController.setInvisible();

        return layout;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (filter.equals(FILTER_ALL)) {
            activityCallback.onFragmentAttached(NavigationAdapter.NAV_ID_ALL_COURSES, getString(R.string.title_section_all_courses));
        } else if (filter.equals(FILTER_MY)) {
            activityCallback.onFragmentAttached(NavigationAdapter.NAV_ID_MY_COURSES, getString(R.string.title_section_my_courses));
        }

        if (courses != null && courses.size() > 0) {
            updateView();
        } else {
            requestCourses(false, false);
        }
    }

    private void updateView() {
        if (isAdded()) {
            if (isMyCoursesFilter() && !UserModel.isLoggedIn(getActivity())) {
                courses = null;

                notificationController.setTitle(R.string.notification_please_login);
                notificationController.setSummary(R.string.notification_please_login_summary);
                notificationController.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        activityCallback.selectDrawerSection(NavigationAdapter.NAV_ID_PROFILE);
                    }
                });
                notificationController.setNotificationVisible(true);
            } else if (isMyCoursesFilter() && (courses == null || courses.size() == 0)) {
                notificationController.setTitle(R.string.notification_no_enrollments);
                notificationController.setSummary(R.string.notification_no_enrollments_summary);
                notificationController.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        activityCallback.selectDrawerSection(NavigationAdapter.NAV_ID_ALL_COURSES);
                    }
                });
                notificationController.setNotificationVisible(true);
            }

            if (courses != null) {
                courseListAdapter.updateCourses(courses);
            } else {
                courseListAdapter.clear();
            }
            activityCallback.updateDrawer();
        }
    }

    @Override
    public void onRefresh() {
        requestCourses(true, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onEnrollButtonClicked(Course course) {
        final ProgressDialog dialog = ProgressDialog.getInstance();
        Result<Course> result = new Result<Course>() {
            @Override
            protected void onSuccess(Course result, DataSource dataSource) {
                dialog.dismiss();
                EventBus.getDefault().post(new EnrollEvent(result));
                if (DateUtil.nowIsAfter(result.available_from)) {
                    onEnterButtonClicked(result);
                }
            }

            @Override
            protected void onError(ErrorCode errorCode) {
                dialog.dismiss();
                if (errorCode == ErrorCode.NO_NETWORK) {
                    NetworkUtil.showNoConnectionToast();
                } else if (errorCode == ErrorCode.NO_AUTH) {
                    ToastUtil.show(R.string.toast_please_log_in);
                    activityCallback.selectDrawerSection(NavigationAdapter.NAV_ID_PROFILE);
                }
            }
        };
        dialog.show(getChildFragmentManager(), ProgressDialog.TAG);
        courseModel.addEnrollment(result, course);
    }

    private boolean isMyCoursesFilter() {
        return filter.equals(CourseListFragment.FILTER_MY);
    }

    private boolean isAllCoursesFilter() {
        return filter.equals(CourseListFragment.FILTER_ALL);
    }

    @Override
    public void onEnterButtonClicked(Course course) {
        if (UserModel.isLoggedIn(getActivity())) {
            Intent intent = new Intent(getActivity(), CourseActivity.class);
            Bundle b = new Bundle();
            b.putParcelable(CourseActivity.ARG_COURSE, course);
            intent.putExtras(b);
            startActivity(intent);
        } else {
            ToastUtil.show(R.string.toast_please_log_in);
            activityCallback.selectDrawerSection(NavigationAdapter.NAV_ID_PROFILE);
        }
    }

    @Override
    public void onDetailButtonClicked(Course course) {
        Intent intent = new Intent(getActivity(), CourseDetailsActivity.class);
        Bundle b = new Bundle();
        b.putParcelable(CourseDetailsActivity.ARG_COURSE, course);
        intent.putExtras(b);
        startActivity(intent);
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(UnenrollEvent event) {
        if (courses != null && courses.contains(event.getCourse())) {
            if (isMyCoursesFilter()) {
                courses.remove(event.getCourse());
            } else {
                courses.set(courses.indexOf(event.getCourse()), event.getCourse());
            }
        }
        updateView();
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(EnrollEvent event) {
        if (isMyCoursesFilter()) {
            if (courses != null && !courses.contains(event.getCourse())) {
                courses.add(event.getCourse());
            }
        } else {
            if (courses != null && courses.contains(event.getCourse())) {
                courses.set(courses.indexOf(event.getCourse()), event.getCourse());
            }
        }
        updateView();
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(LoginEvent event) {
        courses = null;
        if (courseListAdapter != null) {
            courseListAdapter.clear();
        }
        requestCourses(false, false);
    }

    @SuppressWarnings("unused")
    public void onEventMainThread(LogoutEvent event) {
        courses = null;
        if (courseListAdapter != null) {
            courseListAdapter.clear();
        }
        requestCourses(false, false);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (activityCallback != null && !activityCallback.isDrawerOpen()) {
            inflater.inflate(R.menu.refresh, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case R.id.action_refresh:
                onRefresh();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
