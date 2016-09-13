/*
 * Copyright (C) 2016 Jacob Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.klinker.messenger.fragment;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.service.notification.StatusBarNotification;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.telephony.SmsMessage;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialcamera.MaterialCamera;
import com.bumptech.glide.Glide;

import java.util.List;

import xyz.klinker.giphy.Giphy;
import xyz.klinker.messenger.BuildConfig;
import xyz.klinker.messenger.R;
import xyz.klinker.messenger.activity.MessengerActivity;
import xyz.klinker.messenger.adapter.MessageListAdapter;
import xyz.klinker.messenger.api.implementation.ApiUtils;
import xyz.klinker.messenger.data.DataSource;
import xyz.klinker.messenger.data.MimeType;
import xyz.klinker.messenger.data.Settings;
import xyz.klinker.messenger.data.model.Conversation;
import xyz.klinker.messenger.data.model.Draft;
import xyz.klinker.messenger.data.model.Message;
import xyz.klinker.messenger.receiver.MessageListUpdatedReceiver;
import xyz.klinker.messenger.util.AnimationUtils;
import xyz.klinker.messenger.util.ColorUtils;
import xyz.klinker.messenger.util.ContactUtils;
import xyz.klinker.messenger.util.DensityUtil;
import xyz.klinker.messenger.util.PermissionsUtils;
import xyz.klinker.messenger.util.PhoneNumberUtils;
import xyz.klinker.messenger.util.SendUtils;
import xyz.klinker.messenger.util.listener.AudioRecordedListener;
import xyz.klinker.messenger.util.listener.ImageSelectedListener;
import xyz.klinker.messenger.util.listener.TextSelectedListener;
import xyz.klinker.messenger.view.AttachImageView;
import xyz.klinker.messenger.view.AttachLocationView;
import xyz.klinker.messenger.view.ElasticDragDismissFrameLayout;
import xyz.klinker.messenger.view.ElasticDragDismissFrameLayout.ElasticDragDismissCallback;
import xyz.klinker.messenger.view.MaterialTooltip;
import xyz.klinker.messenger.view.RecordAudioView;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment for displaying messages for a certain conversation.
 */
public class MessageListFragment extends Fragment implements
        ImageSelectedListener, AudioRecordedListener, TextSelectedListener {

    private static final String TAG = "MessageListFragment";
    private static final String ARG_TITLE = "title";
    private static final String ARG_PHONE_NUMBERS = "phone_numbers";
    private static final String ARG_COLOR = "color";
    private static final String ARG_COLOR_DARKER = "color_darker";
    private static final String ARG_COLOR_ACCENT = "color_accent";
    private static final String ARG_IS_GROUP = "is_group";
    private static final String ARG_CONVERSATION_ID = "conversation_id";
    private static final String ARG_MUTE_CONVERSATION = "mute_conversation";
    private static final String ARG_MESSAGE_TO_OPEN_ID = "message_to_open";
    private static final String ARG_READ = "read";
    private static final String ARG_IMAGE_URI = "image_uri";
    private static final String ARG_IS_ARCHIVED = "is_archived";

    private static final int PERMISSION_STORAGE_REQUEST = 1;
    private static final int PERMISSION_AUDIO_REQUEST = 2;
    private static final int RESULT_VIDEO_REQUEST = 3;
    private static final int RESULT_GIPHY_REQUEST = 4;
    private static final int PERMISSION_LOCATION_REQUEST = 5;

    private DataSource source;
    private View appBarLayout;
    private Toolbar toolbar;
    private View sendBar;
    private EditText messageEntry;
    private ImageButton attach;
    private FloatingActionButton send;
    private TextView counter;
    private RecyclerView messageList;
    private LinearLayoutManager manager;
    private MessageListAdapter adapter;
    private ElasticDragDismissFrameLayout dragDismissFrameLayout;
    private View attachLayout;
    private FrameLayout attachHolder;
    private LinearLayout attachButtonHolder;
    private ImageButton attachImage;
    private ImageButton captureImage;
    private ImageButton attachGif;
    private ImageButton recordVideo;
    private ImageButton recordAudio;
    private ImageButton attachLocation;
    private View attachedImageHolder;
    private ImageView attachedImage;
    private View removeImage;
    private BroadcastReceiver updatedReceiver;
    private boolean dismissNotification = false;
    private boolean textChanged = false;
    private List<Draft> drafts;

    private Uri attachedUri;
    private String attachedMimeType;

    private AlertDialog detailsChoiceDialog;
    private MaterialTooltip navToolTip;

    public static MessageListFragment newInstance(Conversation conversation) {
        return newInstance(conversation, -1);
    }

    public static MessageListFragment newInstance(Conversation conversation, long messageToOpenId) {
        MessageListFragment fragment = new MessageListFragment();

        Bundle args = new Bundle();
        args.putString(ARG_TITLE, conversation.title);
        args.putString(ARG_PHONE_NUMBERS, conversation.phoneNumbers);
        args.putInt(ARG_COLOR, conversation.colors.color);
        args.putInt(ARG_COLOR_DARKER, conversation.colors.colorDark);
        args.putInt(ARG_COLOR_ACCENT, conversation.colors.colorAccent);
        args.putBoolean(ARG_IS_GROUP, conversation.isGroup());
        args.putLong(ARG_CONVERSATION_ID, conversation.id);
        args.putBoolean(ARG_MUTE_CONVERSATION, conversation.mute);
        args.putBoolean(ARG_READ, conversation.read);
        args.putString(ARG_IMAGE_URI, conversation.imageUri);
        args.putBoolean(ARG_IS_ARCHIVED, conversation.archive);

        if (messageToOpenId != -1) {
            args.putLong(ARG_MESSAGE_TO_OPEN_ID, messageToOpenId);
        }

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle bundle) {
        source = DataSource.getInstance(getContext());
        source.open();

        View view = inflater.inflate(R.layout.fragment_message_list, parent, false);

        appBarLayout = view.findViewById(R.id.app_bar_layout);
        toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        sendBar = view.findViewById(R.id.send_bar);
        messageEntry = (EditText) view.findViewById(R.id.message_entry);
        attach = (ImageButton) view.findViewById(R.id.attach);
        send = (FloatingActionButton) view.findViewById(R.id.send);
        counter = (TextView) view.findViewById(R.id.text_counter);
        messageList = (RecyclerView) view.findViewById(R.id.message_list);
        attachLayout = view.findViewById(R.id.attach_layout);
        attachHolder = (FrameLayout) view.findViewById(R.id.attach_holder);
        attachButtonHolder = (LinearLayout) view.findViewById(R.id.attach_button_holder);
        attachImage = (ImageButton) view.findViewById(R.id.attach_image);
        captureImage = (ImageButton) view.findViewById(R.id.capture_image);
        attachGif = (ImageButton) view.findViewById(R.id.attach_gif);
        recordVideo = (ImageButton) view.findViewById(R.id.record_video);
        recordAudio = (ImageButton) view.findViewById(R.id.record_audio);
        attachLocation = (ImageButton) view.findViewById(R.id.attach_location);
        attachedImageHolder = view.findViewById(R.id.attached_image_holder);
        attachedImage = (ImageView) view.findViewById(R.id.attached_image);
        removeImage = view.findViewById(R.id.remove_image);

        dragDismissFrameLayout = (ElasticDragDismissFrameLayout) view;
        dragDismissFrameLayout.addListener(new ElasticDragDismissCallback() {
            @Override
            public void onDragDismissed() {
                boolean focused = messageEntry.hasFocus();

                dismissKeyboard();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (getActivity() != null) {
                            getActivity().onBackPressed();
                        }
                    }
                }, focused ? 300 : 100);
            }

            @Override
            public void onDrag(float elasticOffset, float elasticOffsetPixels,
                               float rawOffset, float rawOffsetPixels) {

            }
        });

        initSendbar();
        initAttachHolder();
        initToolbar();
        initRecycler();

        Settings settings = Settings.get(getActivity());
        if (settings.useGlobalThemeColor) {
            toolbar.setBackgroundColor(settings.globalColorSet.color);
            send.setBackgroundTintList(ColorStateList.valueOf(settings.globalColorSet.colorAccent));
            messageEntry.setHighlightColor(settings.globalColorSet.colorAccent);
        }

        dismissNotification = true;
        dismissNotification();

        AnimationUtils.animateConversationPeripheralIn(appBarLayout);
        AnimationUtils.animateConversationPeripheralIn(sendBar);

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updatedReceiver = new MessageListUpdatedReceiver(this);
        getActivity().registerReceiver(updatedReceiver,
                MessageListUpdatedReceiver.getIntentFilter());

        showTooltip();
    }

    @Override
    public void onStart() {
        super.onStart();
        dismissNotification = true;
    }

    @Override
    public void onStop() {
        super.onStop();
        dismissNotification = false;

        if (navToolTip != null && navToolTip.isShowing()) {
            navToolTip.hide();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (updatedReceiver != null) {
            getActivity().unregisterReceiver(updatedReceiver);
        }

        if (messageEntry.getText() != null && messageEntry.getText().length() > 0 && textChanged) {
            if (drafts.size() > 0) {
                source.deleteDrafts(getConversationId());
            }

            source.insertDraft(getConversationId(),
                    messageEntry.getText().toString(), MimeType.TEXT_PLAIN);
        } else if (messageEntry.getText() != null && messageEntry.getText().length() == 0 && textChanged) {
            if (drafts.size() > 0) {
                source.deleteDrafts(getConversationId());
            }
        }

        if (attachedUri != null) {
            source.insertDraft(getConversationId(), attachedUri.toString(), attachedMimeType);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        if (adapter != null) {
            adapter.getMessages().close();
        }

        if (source != null) {
            source.close();
        }
    }

    private void initToolbar() {
        String name = getArguments().getString(ARG_TITLE);
        int color = getArguments().getInt(ARG_COLOR);
        int colorAccent = getArguments().getInt(ARG_COLOR_ACCENT);

        toolbar.setTitle(name);
        toolbar.setBackgroundColor(color);

        if (!getResources().getBoolean(R.bool.pin_drawer)) {
            final DrawerLayout drawerLayout = (DrawerLayout) getActivity()
                    .findViewById(R.id.drawer_layout);
            toolbar.setNavigationIcon(R.drawable.ic_menu);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        toolbar.inflateMenu(getArguments().getBoolean(ARG_IS_GROUP) ?
                R.menu.fragment_messages_group : R.menu.fragment_messages);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.menu_call) {
                    ((MessengerActivity) getActivity()).callContact();
                }

                return true;
            }
        });

        setNameAndDrawerColor(getActivity());
        ColorUtils.setCursorDrawableColor(messageEntry, colorAccent);
    }

    private void setNameAndDrawerColor(Activity activity) {
        String name = getArguments().getString(ARG_TITLE);
        String phoneNumber = PhoneNumberUtils.format(getArguments().getString(ARG_PHONE_NUMBERS));
        int colorDarker = getArguments().getInt(ARG_COLOR_DARKER);
        boolean isGroup = getArguments().getBoolean(ARG_IS_GROUP);
        String imageUri = getArguments().getString(ARG_IMAGE_URI);

        TextView nameView = (TextView) activity.findViewById(R.id.drawer_header_reveal_name);
        TextView phoneNumberView = (TextView) activity
                .findViewById(R.id.drawer_header_reveal_phone_number);
        ImageView image = (ImageView) activity.findViewById(R.id.drawer_header_reveal_image);

        // could be null when rotating the device
        if (nameView != null) {
            if (!name.equals(phoneNumber)) {
                nameView.setText(name);
            } else {
                messageEntry.setHint(R.string.type_message);
                nameView.setText("");
            }

            phoneNumberView.setText(phoneNumber);

            if (imageUri != null) {
                Glide.with(getActivity())
                        .load(Uri.parse(imageUri))
                        .into(image);
            } else {
                image.setImageDrawable(null);
            }

            ColorUtils.adjustStatusBarColor(colorDarker, activity);
            ColorUtils.adjustDrawerColor(colorDarker, isGroup, activity);
        }

        NavigationView nav = (NavigationView) activity.findViewById(R.id.navigation_view);
        if (nav != null && getArguments().getBoolean(ARG_IS_ARCHIVED)) {
            nav.getMenu().findItem(R.id.drawer_archive_conversation).setTitle(R.string.menu_move_to_inbox);
        }
    }

    private void initSendbar() {
        String firstName = getArguments().getString(ARG_TITLE).split(" ")[0];
        if (!getArguments().getBoolean(ARG_IS_GROUP)) {
            String hint = getResources().getString(R.string.type_message_to, firstName);
            messageEntry.setHint(hint);
        } else {
            messageEntry.setHint(R.string.type_message);
        }

        messageEntry.setTextSize(Settings.get(getActivity()).largeFont);

        messageEntry.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                boolean handled = false;

                if ((keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN &&
                        keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) ||
                        actionId == EditorInfo.IME_ACTION_SEND) {
                    sendMessage();
                    handled = true;
                }

                return handled;
            }
        });

        messageEntry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (attachLayout.getVisibility() == View.VISIBLE) {
                    attach.performClick();
                }
            }
        });

        messageEntry.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                changeCounterText();
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        int accent = getArguments().getInt(ARG_COLOR_ACCENT);
        send.setBackgroundTintList(ColorStateList.valueOf(accent));
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });
        messageEntry.setHighlightColor(accent);

        removeImage.setBackgroundColor(accent);
        removeImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearAttachedData();
            }
        });
    }

    private void changeCounterText() {
        if (attachedUri == null && !getArguments().getBoolean(ARG_IS_GROUP)) {
            int[] count = SmsMessage.calculateLength(messageEntry.getText().toString(), false);

            if ((count[0] > 1 && count[0] < 4) || (count[0] == 1 && count[2] < 30)) {
                //noinspection AndroidLintSetTextI18n
                counter.setText(count[0] + "/" + count[2]);
            } else {
                if (count[0] >= 4) {
                    counter.setText(/*R.string.mms_message*/ null);
                } else {
                    counter.setText(null);
                }
            }
        } else {
            counter.setText(/*R.string.mms_message*/ null);
        }
    }

    private void initAttachHolder() {
        Settings settings = Settings.get(getActivity());
        if (settings.useGlobalThemeColor) {
            attachButtonHolder.setBackgroundColor(settings.globalColorSet.color);
        } else {
            attachButtonHolder.setBackgroundColor(getArguments().getInt(ARG_COLOR));
        }

        attach.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ValueAnimator animator;

                if (attachLayout.getVisibility() == View.VISIBLE) {
                    dragDismissFrameLayout.setEnabled(true);
                    animator = ValueAnimator.ofInt(attachLayout.getHeight(), 0);
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            attachHolder.removeAllViews();
                            attachLayout.setVisibility(View.GONE);
                        }
                    });
                } else {
                    dragDismissFrameLayout.setEnabled(false);
                    attachImage();
                    attachLayout.setVisibility(View.VISIBLE);
                    animator = ValueAnimator.ofInt(0,
                            getResources().getDimensionPixelSize(R.dimen.attach_menu_height));
                }

                final ViewGroup.MarginLayoutParams params =
                        (ViewGroup.MarginLayoutParams) attachLayout.getLayoutParams();

                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        params.height = (Integer) valueAnimator.getAnimatedValue();
                        attachLayout.requestLayout();
                    }
                });

                animator.setDuration(200);
                animator.start();
            }
        });

        attachImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                attachImage();
            }
        });

        captureImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                captureImage();
            }
        });

        attachGif.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                attachGif();
            }
        });

        recordVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recordVideo();
            }
        });

        recordAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recordAudio();
            }
        });

        attachLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attachLocation();
            }
        });
    }

    private void initRecycler() {
        ColorUtils.changeRecyclerOverscrollColors(messageList, getArguments().getInt(ARG_COLOR));

        manager = new LinearLayoutManager(getActivity());
        manager.setStackFromEnd(true);
        messageList.setLayoutManager(manager);

        loadMessages();
    }

    private void dismissNotification() {
        try {
            if (dismissNotification && notificationActive()) {
                NotificationManagerCompat.from(getContext())
                        .cancel((int) getConversationId());
                new ApiUtils().dismissNotification(Settings.get(getActivity()).accountId,
                        getConversationId());
            }
        } catch (Exception e) {

        }
    }

    private boolean notificationActive() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        } else {
            NotificationManager manager = (NotificationManager) getContext().getSystemService(
                    Context.NOTIFICATION_SERVICE);
            StatusBarNotification[] notifications = manager.getActiveNotifications();

            for (StatusBarNotification notification : notifications) {
                if (notification.getId() == (int) getConversationId()) {
                    return true;
                }
            }

            return false;
        }
    }

    public void loadMessages() {
        final Handler handler = new Handler();
        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    Thread.sleep(AnimationUtils.EXPAND_CONVERSATION_DURATION);
                } catch (Exception e) {

                }

                long conversationId = getConversationId();

                if (source.isOpen()) {
                    long startTime = System.currentTimeMillis();
                    final Cursor cursor = source.getMessages(conversationId);
                    drafts = source.getDrafts(conversationId);

                    final int position = findMessagePositionFromId(cursor);

                    Log.v("message_load", "load took " + (
                            System.currentTimeMillis() - startTime) + " ms");

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            setMessages(cursor);
                            setDrafts(drafts);

                            if (position != -1) {
                                messageList.scrollToPosition(position);
                            }

                            textChanged = false;
                            messageEntry.addTextChangedListener(new TextWatcher() {
                                @Override public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
                                @Override public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
                                @Override public void afterTextChanged(Editable editable) {
                                    textChanged = true;
                                }
                            });
                        }
                    });

                    if (!getArguments().getBoolean(ARG_IS_GROUP)) {
                        String number = getArguments().getString(ARG_PHONE_NUMBERS);
                        final String name = ContactUtils.findContactNames(number, getActivity());
                        if (!name.equals(getArguments().getString(ARG_TITLE)) &&
                                !PhoneNumberUtils.checkEquality(name, number)) {
                            Log.v(TAG, "contact name and conversation name do not match, updating");
                            source.updateConversationTitle(
                                    getArguments().getLong(ARG_CONVERSATION_ID), name);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    toolbar.setTitle(name);
                                }
                            });
                        }
                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                }

                try {
                    if (source.isOpen()) {
                        dismissNotification();
                        source.readConversation(getContext(), conversationId);
                    }
                } catch (IllegalStateException e) {

                }
            }
        }).start();
    }

    private int findMessagePositionFromId(Cursor cursor) {
        if (getArguments() != null && getArguments()
                .containsKey(ARG_MESSAGE_TO_OPEN_ID) &&
                cursor != null && cursor.moveToFirst()) {
            long id = getArguments().getLong(ARG_MESSAGE_TO_OPEN_ID);

            do {
                if (cursor.getLong(0) == id) {
                    return cursor.getPosition();
                }
            } while (cursor.moveToNext());
        }

        return -1;
    }

    private void setMessages(Cursor messages) {
        if (adapter != null) {
            adapter.addMessage(messages);
        } else {
            adapter = new MessageListAdapter(messages, getArguments().getInt(ARG_COLOR),
                    Settings.get(getActivity()).useGlobalThemeColor ?
                            Settings.get(getActivity()).globalColorSet.colorAccent :
                            getArguments().getInt(ARG_COLOR_ACCENT),
                    getArguments().getBoolean(ARG_IS_GROUP), manager, this);
            messageList.setAdapter(adapter);

            messageList.animate().withLayer()
                    .alpha(1f).setDuration(100).setStartDelay(0).setListener(null);
        }
    }

    private void setDrafts(List<Draft> drafts) {
        for (Draft draft : drafts) {
            if (draft.mimeType.equals(MimeType.TEXT_PLAIN)) {
                messageEntry.setText(draft.data);
            } else if (MimeType.isStaticImage(draft.mimeType)) {
                attachImage(Uri.parse(draft.data));
            }
        }
    }

    private void sendMessage() {
        if (PermissionsUtils.checkRequestMainPermissions(getActivity())) {
            PermissionsUtils.startMainPermissionRequest(getActivity());
        } else if (Settings.get(getActivity()).primary &&
                !PermissionsUtils.isDefaultSmsApp(getActivity())) {
            PermissionsUtils.setDefaultSmsApp(getActivity());
        } else {
            final String message = messageEntry.getText().toString().trim();
            final Uri uri = attachedUri;
            final String mimeType = attachedMimeType;

            if (message.length() > 0 || attachedUri != null) {
                final Message m = new Message();
                m.conversationId = getConversationId();
                m.type = Message.TYPE_SENDING;
                m.data = message;
                m.timestamp = System.currentTimeMillis();
                m.mimeType = MimeType.TEXT_PLAIN;
                m.read = true;
                m.seen = true;
                m.from = null;
                m.color = null;

                if (adapter.getItemCount() == 1 &&
                        adapter.getItemViewType(0) == Message.TYPE_INFO) {
                    source.deleteMessage(adapter.getItemId(0));
                }

                if (message.length() != 0) {
                    source.insertMessage(getActivity(), m, m.conversationId);
                    loadMessages();
                }

                messageEntry.setText(null);

                ConversationListFragment fragment = (ConversationListFragment) getActivity()
                        .getSupportFragmentManager().findFragmentById(R.id.conversation_list_container);

                if (fragment != null) {
                    fragment.notifyOfSentMessage(m);
                }

                if (uri != null) {
                    m.data = uri.toString();
                    m.mimeType = mimeType;

                    if (m.id != 0) {
                        m.id = 0;
                    }

                    source.insertMessage(getActivity(), m, m.conversationId);
                    loadMessages();
                }

                clearAttachedData();

                if (fragment != null) {
                    fragment.notifyOfSentMessage(m);
                }

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Uri imageUri = SendUtils.send(getContext(), message,
                                getArguments().getString(ARG_PHONE_NUMBERS), uri, mimeType);
                        source.deleteDrafts(getConversationId());

                        if (imageUri != null && uri != null) {
                            Cursor cursor = source.searchMessages(uri.toString());
                            if (cursor != null && cursor.moveToFirst()) {
                                source.updateMessageData(cursor.getLong(0), imageUri.toString());
                                cursor.close();
                            }
                        }
                    }
                }).start();

                if (notificationActive()) {
                    NotificationManagerCompat.from(getContext())
                            .cancel((int) getConversationId());
                }
            }
        }
    }

    private void attachImage() {
        prepareAttachHolder(0);
        if (ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            attachHolder.addView(new AttachImageView(getActivity(), this,
                    Settings.get(getActivity()).useGlobalThemeColor ?
                            Settings.get(getActivity()).globalColorSet.color :
                            getArguments().getInt(ARG_COLOR)));
        } else {
            attachPermissionRequest(PERMISSION_STORAGE_REQUEST,
                    Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    private void captureImage() {
        prepareAttachHolder(1);

        Camera2BasicFragment fragment = Camera2BasicFragment.newInstance();
        getFragmentManager().beginTransaction().add(R.id.attach_holder, fragment).commit();
        fragment.attachImageSelectedListener(this);
    }

    private void attachGif() {
        prepareAttachHolder(2);
        new Giphy.Builder(getActivity(), BuildConfig.GIPHY_API_KEY)
                .maxFileSize(1024 * 1024)
                .start(RESULT_GIPHY_REQUEST);
    }

    private void recordVideo() {
        prepareAttachHolder(3);

        MaterialCamera camera = new MaterialCamera(getActivity())
                .saveDir(getActivity().getFilesDir().getPath())
                .qualityProfile(MaterialCamera.QUALITY_LOW)
                .maxAllowedFileSize(1024 * 1024)
                .allowRetry(false)
                .autoSubmit(true)
                .showPortraitWarning(false);

        if (Settings.get(getActivity()).useGlobalThemeColor) {
            camera.primaryColor(Settings.get(getActivity()).globalColorSet.color);
        } else {
            camera.primaryColor(getArguments().getInt(ARG_COLOR));
        }

        camera.start(RESULT_VIDEO_REQUEST);
    }

    private void recordAudio() {
        prepareAttachHolder(4);
        if (ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(getContext(),
                        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            attachHolder.addView(new RecordAudioView(getActivity(), this,
                    Settings.get(getActivity()).useGlobalThemeColor ?
                            Settings.get(getActivity()).globalColorSet.colorAccent :
                            getArguments().getInt(ARG_COLOR_ACCENT)));
        } else {
            attachPermissionRequest(PERMISSION_AUDIO_REQUEST,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO);
        }
    }

    private void attachLocation() {
        prepareAttachHolder(5);
        if (ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(getContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            attachHolder.addView(new AttachLocationView(getActivity(), this, this,
                    Settings.get(getActivity()).useGlobalThemeColor ?
                            Settings.get(getActivity()).globalColorSet.colorAccent :
                            getArguments().getInt(ARG_COLOR_ACCENT)));
        } else {
            attachPermissionRequest(PERMISSION_LOCATION_REQUEST,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void attachPermissionRequest(final int permissionRequestCode, final String... permissions) {
        getLayoutInflater(null).inflate(R.layout.permission_request, attachHolder, true);
        Button request = (Button) attachHolder.findViewById(R.id.permission_needed);
        request.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestPermissions(permissions, permissionRequestCode);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_STORAGE_REQUEST) {
            attachImage();
        } else if (requestCode == PERMISSION_AUDIO_REQUEST) {
            recordAudio();
        } else if (requestCode == PERMISSION_LOCATION_REQUEST) {
            attachLocation();
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RESULT_VIDEO_REQUEST) {
            onBackPressed();

            if (resultCode == RESULT_OK) {
                Log.v("video result", "saved to " + data.getDataString());
                attachImage(data.getData());
                attachedMimeType = MimeType.VIDEO_MP4;
            } else if (data != null) {
                Exception e = (Exception) data.getSerializableExtra(MaterialCamera.ERROR_EXTRA);
                e.printStackTrace();
                Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == RESULT_GIPHY_REQUEST) {
            onBackPressed();

            if (resultCode == RESULT_OK) {
                Log.v("gif result", "saved to " + data.getDataString());
                attachImage(data.getData());
                attachedMimeType = MimeType.IMAGE_GIF;
            }
        }
    }

    private void prepareAttachHolder(int positionToBold) {
        dismissKeyboard();
        attachHolder.removeAllViews();

        for (int i = 0; i < attachButtonHolder.getChildCount(); i++) {
            if (positionToBold == i) {
                attachButtonHolder.getChildAt(i).setAlpha(1.0f);
            } else {
                attachButtonHolder.getChildAt(i).setAlpha(0.5f);
            }
        }
    }

    private void clearAttachedData() {
        source.deleteDrafts(getConversationId());
        attachedImageHolder.setVisibility(View.GONE);
        attachedImage.setImageDrawable(null);
        attachedUri = null;
        attachedMimeType = null;
        changeCounterText();
    }

    private void dismissKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(messageEntry.getWindowToken(), 0);
    }

    @Override
    public void onImageSelected(Uri uri) {
        onBackPressed();
        attachImage(uri);
    }

    @Override
    public void onRecorded(Uri uri) {
        onBackPressed();
        attachAudio(uri);
    }

    @Override
    public void onTextSelected(String text) {
        messageEntry.setText(text);
    }

    private void attachImage(Uri uri) {
        clearAttachedData();
        attachedUri = uri;
        attachedMimeType = MimeType.IMAGE_JPG;

        attachedImageHolder.setVisibility(View.VISIBLE);

        try {
            Glide.with(getActivity())
                    .load(uri)
                    .placeholder(R.drawable.ic_image_sending)
                    .into(attachedImage);
            changeCounterText();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void attachAudio(Uri uri) {
        clearAttachedData();
        attachedUri = uri;
        attachedMimeType = MimeType.AUDIO_MP4;

        attachedImageHolder.setVisibility(View.VISIBLE);
        attachedImage.setImageResource(R.drawable.ic_audio_sent);
        changeCounterText();
    }

    public boolean onBackPressed() {
        dismissDetailsChoiceDialog();

        if (attachLayout != null && attachLayout.getVisibility() == View.VISIBLE) {
            attach.performClick();
            return true;
        }

        return false;
    }

    public long getConversationId() {
        return getArguments().getLong(ARG_CONVERSATION_ID);
    }

    public boolean isDragging() {
        return dragDismissFrameLayout.isDragging();
    }

    public void setDetailsChoiceDialog(AlertDialog dialog) {
        this.detailsChoiceDialog = dialog;
    }

    private void dismissDetailsChoiceDialog() {
        if (detailsChoiceDialog != null && detailsChoiceDialog.isShowing()) {
            detailsChoiceDialog.dismiss();
            detailsChoiceDialog = null;
        }
    }

    public boolean isRecyclerScrolling() {
        return messageList != null && messageList.getScrollState() != RecyclerView.SCROLL_STATE_IDLE;
    }

    public void showTooltip() {
        if (!getResources().getBoolean(R.bool.pin_drawer) && !Settings.get(getActivity()).seenConvoNavToolTip) {
            MaterialTooltip.Options options = new MaterialTooltip.Options(
                    56 + 24 + 12,
                    12, 275, Settings.get(getActivity()).useGlobalThemeColor ?
                    Settings.get(getActivity()).globalColorSet.color :
                    getArguments().getInt(ARG_COLOR))
                    .setText(getString(R.string.navigation_drawer_conversation_hint));

            navToolTip = new MaterialTooltip(getActivity(), options);
            navToolTip.show(new MaterialTooltip.Callback() {
                @Override
                public void onGotIt() {
                    try {
                        Settings.get(getActivity())
                                .setValue(getString(R.string.pref_seen_convo_nav_tooltip), true);

                        new ApiUtils().updateSeenTooltip(Settings.get(getActivity()).accountId, true);
                    } catch (IllegalStateException e) {
                        // not attached to activity
                    }
                }
            });
        }
    }
}
