/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Modern Profile Implementation by Assistant
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ProfileGalleryView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Gifts.ProfileGiftsContainer;
import org.telegram.ui.Cells.ModernProfileHeaderCell;
import org.telegram.ui.Cells.ModernActionCard;
import org.telegram.ui.Cells.ModernInfoCard;

import java.util.ArrayList;

public class ModernProfileActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    // Profile types
    private static final int PROFILE_TYPE_USER = 0;
    private static final int PROFILE_TYPE_BUSINESS = 1;
    private static final int PROFILE_TYPE_GROUP = 2;
    private static final int PROFILE_TYPE_CHANNEL = 3;
    private static final int PROFILE_TYPE_TOPIC = 4;

    // View types for RecyclerView
    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_INFO_CARD = 1;
    private static final int VIEW_TYPE_ACTION_CARD = 2;
    private static final int VIEW_TYPE_GIFTS_CARD = 3;
    private static final int VIEW_TYPE_BUSINESS_CARD = 4;
    private static final int VIEW_TYPE_SECTION_DIVIDER = 5;

    // Views
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private ListAdapter listAdapter;
    private UndoView undoView;

    // Profile data
    private long userId;
    private long chatId;
    private long topicId;
    private long dialogId;
    private int profileType;
    private boolean myProfile;
    private boolean openGifts;

    // Current data
    private TLRPC.User currentUser;
    private TLRPC.Chat currentChat;
    private TLRPC.UserFull userInfo;
    private TLRPC.ChatFull chatInfo;

    // UI State
    private boolean expandedMode = false;
    private float expandProgress = 0f;
    private ValueAnimator expandAnimator;

    // Theme
    private Theme.ResourcesProvider resourcesProvider;

    public static ModernProfileActivity of(long dialogId) {
        Bundle args = new Bundle();
        if (DialogObject.isUserDialog(dialogId)) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }
        args.putLong("dialog_id", dialogId);
        return new ModernProfileActivity(args);
    }

    public ModernProfileActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        userId = arguments.getLong("user_id", 0);
        chatId = arguments.getLong("chat_id", 0);
        topicId = arguments.getLong("topic_id", 0);
        openGifts = arguments.getBoolean("open_gifts", false);
        myProfile = arguments.getBoolean("my_profile", false);

        if (userId != 0) {
            dialogId = userId;
            currentUser = getMessagesController().getUser(userId);
            if (currentUser == null) {
                return false;
            }
            profileType = currentUser.bot ? 
                (currentUser.bot_business ? PROFILE_TYPE_BUSINESS : PROFILE_TYPE_USER) : 
                PROFILE_TYPE_USER;
                
            userInfo = getMessagesController().getUserFull(userId);
            getMessagesController().loadFullUser(currentUser, classGuid, true);
        } else if (chatId != 0) {
            dialogId = -chatId;
            currentChat = getMessagesController().getChat(chatId);
            if (currentChat == null) {
                return false;
            }
            
            if (topicId != 0) {
                profileType = PROFILE_TYPE_TOPIC;
            } else if (ChatObject.isChannel(currentChat)) {
                profileType = PROFILE_TYPE_CHANNEL;
            } else {
                profileType = PROFILE_TYPE_GROUP;
            }
            
            chatInfo = getMessagesController().getChatFull(chatId);
            getMessagesController().loadFullChat(chatId, classGuid, true);
        } else {
            return false;
        }

        // Register observers
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.userInfoDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.starUserGiftsLoaded);

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.userInfoDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.starUserGiftsLoaded);
    }

    @Override
    public View createView(Context context) {
        hasOwnBackground = true;
        
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setCastShadows(false);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setOccupyStatusBar(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        // Create action bar menu
        createActionBarMenu();

        // Main container
        FrameLayout fragmentView = new FrameLayout(context) {
            private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            
            @Override
            protected void onDraw(Canvas canvas) {
                // Draw modern gradient background
                drawModernBackground(canvas);
                super.onDraw(canvas);
            }
            
            private void drawModernBackground(Canvas canvas) {
                int color1 = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
                int color2 = Theme.blendOver(color1, Theme.multAlpha(
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.02f));
                
                if (profileType == PROFILE_TYPE_USER && currentUser != null && !currentUser.bot) {
                    // Subtle gradient for users
                    LinearGradient gradient = new LinearGradient(
                        0, 0, 0, getMeasuredHeight() * 0.3f,
                        color2, color1, Shader.TileMode.CLAMP
                    );
                    backgroundPaint.setShader(gradient);
                } else {
                    backgroundPaint.setColor(color1);
                    backgroundPaint.setShader(null);
                }
                
                canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
            }
        };
        fragmentView.setWillNotDraw(false);

        // Create list view
        listView = new RecyclerListView(context, resourcesProvider) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                if (openGifts && !expandedMode) {
                    // Auto-expand to gifts section if requested
                    scrollToGifts();
                }
            }
        };
        
        layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        
        listView.setLayoutManager(layoutManager);
        listAdapter = new ListAdapter(context);
        listView.setAdapter(listAdapter);
        listView.setClipToPadding(false);
        listView.setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);

        fragmentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Undo view
        undoView = new UndoView(context, resourcesProvider);
        fragmentView.addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        return fragmentView;
    }

    private void createActionBarMenu() {
        ActionBarMenu menu = actionBar.createMenu();
        
        if (profileType == PROFILE_TYPE_USER) {
            if (myProfile) {
                menu.addItem(1, R.drawable.msg_edit);
            } else {
                menu.addItem(2, R.drawable.msg_call);
                menu.addItem(3, R.drawable.msg_video);
            }
        } else if (profileType == PROFILE_TYPE_CHANNEL || profileType == PROFILE_TYPE_GROUP) {
            if (ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_CHANGE_INFO)) {
                menu.addItem(4, R.drawable.msg_edit);
            }
        }
        
        menu.addItem(0, R.drawable.ic_ab_other);
    }

    private void scrollToGifts() {
        // Implementation to scroll to gifts section
        // This would be called when openGifts is true
        if (listAdapter != null) {
            for (int i = 0; i < listAdapter.getItemCount(); i++) {
                if (listAdapter.getItemViewType(i) == VIEW_TYPE_GIFTS_CARD) {
                    listView.smoothScrollToPosition(i);
                    break;
                }
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || 
                (mask & MessagesController.UPDATE_MASK_NAME) != 0 ||
                (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                updateProfileData();
            }
        } else if (id == NotificationCenter.userInfoDidLoad || 
                   id == NotificationCenter.chatInfoDidLoad ||
                   id == NotificationCenter.starUserGiftsLoaded) {
            updateProfileData();
        }
    }

    private void updateProfileData() {
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }
    
    private void setupActionCard(ModernActionCard actionCard) {
        if (profileType == PROFILE_TYPE_USER && currentUser != null) {
            if (myProfile) {
                actionCard.addAction(ModernActionCard.ACTION_EDIT_PROFILE, 
                    LocaleController.getString(R.string.EditProfile), R.drawable.msg_edit, true);
            } else {
                actionCard.addAction(ModernActionCard.ACTION_SEND_MESSAGE, 
                    LocaleController.getString(R.string.SendMessage), R.drawable.msg_message, true);
                    
                if (!currentUser.bot) {
                    actionCard.addAction(ModernActionCard.ACTION_CALL, 
                        LocaleController.getString(R.string.Call), R.drawable.msg_call);
                    actionCard.addAction(ModernActionCard.ACTION_VIDEO_CALL, 
                        LocaleController.getString(R.string.VideoCall), R.drawable.msg_video);
                }
                
                if (!currentUser.contact && !currentUser.bot) {
                    actionCard.addAction(ModernActionCard.ACTION_ADD_CONTACT, 
                        LocaleController.getString(R.string.AddToContacts), R.drawable.msg_addcontact);
                }
                
                actionCard.addAction(ModernActionCard.ACTION_SHARE_CONTACT, 
                    LocaleController.getString(R.string.ShareContact), R.drawable.msg_share);
            }
        } else if (currentChat != null) {
            actionCard.addAction(ModernActionCard.ACTION_SEND_MESSAGE, 
                LocaleController.getString(R.string.SendMessage), R.drawable.msg_message, true);
                
            if (ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_CHANGE_INFO)) {
                actionCard.addAction(ModernActionCard.ACTION_EDIT_CHAT, 
                    LocaleController.getString(R.string.Edit), R.drawable.msg_edit);
            }
            
            actionCard.addAction(ModernActionCard.ACTION_MUTE_NOTIFICATIONS, 
                LocaleController.getString(R.string.MuteNotifications), R.drawable.msg_mute);
        }
    }
    
    private void handleActionClick(int actionId) {
        switch (actionId) {
            case ModernActionCard.ACTION_SEND_MESSAGE:
                Bundle args = new Bundle();
                if (userId != 0) {
                    args.putLong("user_id", userId);
                } else {
                    args.putLong("chat_id", chatId);
                    if (topicId != 0) {
                        args.putInt("message_id", (int) topicId);
                    }
                }
                presentFragment(new ChatActivity(args), true);
                break;
                
            case ModernActionCard.ACTION_EDIT_PROFILE:
                presentFragment(new UserInfoActivity());
                break;
                
            case ModernActionCard.ACTION_CALL:
                // Implement call functionality
                break;
                
            case ModernActionCard.ACTION_VIDEO_CALL:
                // Implement video call functionality
                break;
                
            case ModernActionCard.ACTION_ADD_CONTACT:
                // Implement add contact functionality
                break;
                
            case ModernActionCard.ACTION_SHARE_CONTACT:
                // Implement share contact functionality
                break;
                
            case ModernActionCard.ACTION_EDIT_CHAT:
                // Implement edit chat functionality
                break;
                
            case ModernActionCard.ACTION_MUTE_NOTIFICATIONS:
                // Implement mute notifications functionality
                break;
        }
    }

    private class ListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private Context context;
        private ArrayList<Item> items = new ArrayList<>();

        public ListAdapter(Context context) {
            this.context = context;
            updateItems();
        }

        private void updateItems() {
            items.clear();
            
            // Header with avatar and basic info
            items.add(new Item(VIEW_TYPE_HEADER));
            
            // Info card with details
            items.add(new Item(VIEW_TYPE_INFO_CARD));
            
            // Action buttons card
            items.add(new Item(VIEW_TYPE_ACTION_CARD));
            
            // Gifts card (if applicable)
            if (profileType == PROFILE_TYPE_USER && userInfo != null) {
                items.add(new Item(VIEW_TYPE_GIFTS_CARD));
            }
            
            // Business info card (if applicable)
            if (profileType == PROFILE_TYPE_BUSINESS || 
                (profileType == PROFILE_TYPE_USER && currentUser != null && currentUser.bot_business)) {
                items.add(new Item(VIEW_TYPE_BUSINESS_CARD));
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_HEADER:
                    view = new ModernProfileHeaderCell(context, resourcesProvider);
                    break;
                case VIEW_TYPE_INFO_CARD:
                    view = new ModernInfoCard(context, resourcesProvider);
                    break;
                case VIEW_TYPE_ACTION_CARD:
                    view = new ModernActionCard(context, resourcesProvider);
                    break;
                case VIEW_TYPE_GIFTS_CARD:
                    view = new ModernGiftsCard(context);
                    break;
                case VIEW_TYPE_BUSINESS_CARD:
                    view = new ModernBusinessCard(context);
                    break;
                default:
                    view = new View(context);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            int type = getItemViewType(position);
            switch (type) {
                case VIEW_TYPE_HEADER:
                    ModernProfileHeaderCell headerCell = (ModernProfileHeaderCell) holder.itemView;
                    if (profileType == PROFILE_TYPE_USER && currentUser != null) {
                        headerCell.setUser(currentUser, userInfo, currentAccount);
                    } else if (currentChat != null) {
                        headerCell.setChat(currentChat, chatInfo, currentAccount);
                    }
                    break;
                case VIEW_TYPE_INFO_CARD:
                    ModernInfoCard infoCard = (ModernInfoCard) holder.itemView;
                    infoCard.setInfoClickListener(new ModernInfoCard.InfoClickListener() {
                        @Override
                        public void onInfoClick(String type, String value) {
                            // Handle info clicks
                        }

                        @Override
                        public void onLinkClick(String url) {
                            // Handle link clicks
                        }

                        @Override
                        public void onPhoneClick(String phone) {
                            // Handle phone clicks
                        }

                        @Override
                        public void onUsernameClick(String username) {
                            // Handle username clicks
                        }
                    });
                    
                    if (profileType == PROFILE_TYPE_USER && currentUser != null) {
                        infoCard.setUserData(currentUser, userInfo);
                    } else if (currentChat != null) {
                        infoCard.setChatData(currentChat, chatInfo);
                    }
                    break;
                case VIEW_TYPE_ACTION_CARD:
                    ModernActionCard actionCard = (ModernActionCard) holder.itemView;
                    actionCard.setActionClickListener(new ModernActionCard.ActionClickListener() {
                        @Override
                        public void onActionClick(int actionId) {
                            handleActionClick(actionId);
                        }
                    });
                    
                    // Set up actions based on profile type
                    actionCard.clearActions();
                    setupActionCard(actionCard);
                    break;
                case VIEW_TYPE_GIFTS_CARD:
                    ((ModernGiftsCard) holder.itemView).setData();
                    break;
                case VIEW_TYPE_BUSINESS_CARD:
                    ((ModernBusinessCard) holder.itemView).setData();
                    break;
            }
        }
    }

    private static class Item {
        int type;
        Object data;

        Item(int type) {
            this.type = type;
        }

        Item(int type, Object data) {
            this.type = type;
            this.data = data;
        }
    }

    // Modern UI Components using the new custom cells

    private class ModernInfoCard extends FrameLayout {
        public ModernInfoCard(Context context) {
            super(context);
            createCard();
        }

        private void createCard() {
            // Modern card styling with rounded corners and subtle elevation
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            background.setCornerRadius(dp(12));
            background.setStroke(dp(1), Theme.getColor(Theme.key_divider, resourcesProvider));
            setBackground(background);

            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) getLayoutParams();
            params.setMargins(dp(16), dp(8), dp(16), dp(8));
            setPadding(dp(16), dp(16), dp(16), dp(16));
        }

        public void setData() {
            removeAllViews();
            
            if (profileType == PROFILE_TYPE_USER && currentUser != null) {
                addUserInfo();
            } else if (currentChat != null) {
                addChatInfo();
            }
        }

        private void addUserInfo() {
            if (userInfo != null) {
                // Bio
                if (!TextUtils.isEmpty(userInfo.about)) {
                    addInfoRow(getString(R.string.UserBio), userInfo.about);
                }
                
                // Username
                if (!TextUtils.isEmpty(currentUser.username)) {
                    addInfoRow(getString(R.string.Username), "@" + currentUser.username);
                }
                
                // Phone (if available)
                if (!TextUtils.isEmpty(currentUser.phone)) {
                    addInfoRow(getString(R.string.PhoneNumber), "+" + currentUser.phone);
                }
            }
        }

        private void addChatInfo() {
            if (chatInfo != null) {
                // Description
                if (!TextUtils.isEmpty(chatInfo.about)) {
                    addInfoRow(getString(R.string.DescriptionPlaceholder), chatInfo.about);
                }
                
                // Username
                if (!TextUtils.isEmpty(currentChat.username)) {
                    addInfoRow(getString(R.string.Username), "@" + currentChat.username);
                }
            }
        }

        private void addInfoRow(String label, String value) {
            TextView labelView = new TextView(getContext());
            labelView.setTextSize(14);
            labelView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            labelView.setText(label);
            labelView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            addView(labelView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, getChildCount() > 0 ? 16 : 0, 0, 4));

            TextView valueView = new TextView(getContext());
            valueView.setTextSize(16);
            valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            valueView.setText(value);
            valueView.setMaxLines(3);
            valueView.setEllipsize(TextUtils.TruncateAt.END);
            addView(valueView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 0, 0, 0));
        }
    }

    private class ModernActionCard extends FrameLayout {
        public ModernActionCard(Context context) {
            super(context);
            createCard();
        }

        private void createCard() {
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            background.setCornerRadius(dp(12));
            background.setStroke(dp(1), Theme.getColor(Theme.key_divider, resourcesProvider));
            setBackground(background);

            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) getLayoutParams();
            params.setMargins(dp(16), dp(8), dp(16), dp(8));
            setPadding(dp(8), dp(8), dp(8), dp(8));
        }

        public void setData() {
            removeAllViews();
            
            if (profileType == PROFILE_TYPE_USER) {
                addUserActions();
            } else {
                addChatActions();
            }
        }

        private void addUserActions() {
            if (myProfile) {
                addActionButton(getString(R.string.EditProfile), R.drawable.msg_edit, () -> {
                    presentFragment(new UserInfoActivity());
                });
            } else {
                addActionButton(getString(R.string.SendMessage), R.drawable.msg_message, () -> {
                    Bundle args = new Bundle();
                    args.putLong("user_id", userId);
                    presentFragment(new ChatActivity(args), true);
                });
                
                if (currentUser != null && !currentUser.bot) {
                    addActionButton(getString(R.string.Call), R.drawable.msg_call, () -> {
                        // Implement call action
                    });
                }
            }
        }

        private void addChatActions() {
            addActionButton(getString(R.string.SendMessage), R.drawable.msg_message, () -> {
                Bundle args = new Bundle();
                args.putLong("chat_id", chatId);
                if (topicId != 0) {
                    args.putInt("message_id", (int) topicId);
                }
                presentFragment(new ChatActivity(args), true);
            });
        }

        private void addActionButton(String text, int icon, Runnable action) {
            FrameLayout button = new FrameLayout(getContext());
            
            GradientDrawable buttonBg = new GradientDrawable();
            buttonBg.setShape(GradientDrawable.RECTANGLE);
            buttonBg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            buttonBg.setCornerRadius(dp(8));
            button.setBackground(buttonBg);
            
            ScaleStateListAnimator.apply(button);
            
            button.setOnClickListener(v -> action.run());

            ImageView iconView = new ImageView(getContext());
            iconView.setImageResource(icon);
            iconView.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourcesProvider), 
                PorterDuff.Mode.SRC_IN));
            button.addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | Gravity.LEFT, 16, 0, 0, 0));

            TextView textView = new TextView(getContext());
            textView.setText(text);
            textView.setTextSize(16);
            textView.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourcesProvider));
            textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            button.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 52, 0, 16, 0));

            addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP, 8, getChildCount() > 0 ? 8 : 0, 8, 0));
        }
    }

    private class ModernGiftsCard extends FrameLayout {
        private ProfileGiftsContainer giftsContainer;

        public ModernGiftsCard(Context context) {
            super(context);
            createCard();
        }

        private void createCard() {
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            background.setCornerRadius(dp(12));
            background.setStroke(dp(1), Theme.getColor(Theme.key_divider, resourcesProvider));
            setBackground(background);

            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) getLayoutParams();
            params.setMargins(dp(16), dp(8), dp(16), dp(8));

            // Title
            TextView titleView = new TextView(getContext());
            titleView.setText(getString(R.string.ProfileGifts));
            titleView.setTextSize(18);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 16, 16, 16, 8));

            // Gifts container
            giftsContainer = new ProfileGiftsContainer(ModernProfileActivity.this, getContext(), currentAccount, dialogId, resourcesProvider);
            addView(giftsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.TOP, 0, 48, 0, 16));
        }

        public void setData() {
            if (giftsContainer != null) {
                // The gifts container will handle its own data loading and display
            }
        }
    }

    private class ModernBusinessCard extends FrameLayout {
        public ModernBusinessCard(Context context) {
            super(context);
            createCard();
        }

        private void createCard() {
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            background.setCornerRadius(dp(12));
            background.setStroke(dp(1), Theme.getColor(Theme.key_divider, resourcesProvider));
            setBackground(background);

            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) getLayoutParams();
            params.setMargins(dp(16), dp(8), dp(16), dp(8));
            setPadding(dp(16), dp(16), dp(16), dp(16));
        }

        public void setData() {
            removeAllViews();
            
            // Title
            TextView titleView = new TextView(getContext());
            titleView.setText(getString(R.string.BusinessInfo));
            titleView.setTextSize(18);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 0, 0, 16));

            // Business details would be added here
            // For now, showing placeholder
            TextView infoView = new TextView(getContext());
            infoView.setText("Business information will be displayed here");
            infoView.setTextSize(16);
            infoView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            addView(infoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 42, 0, 0));
        }
    }
} 