package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RippleDrawable;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;
import java.util.List;

public class ModernActionCard extends FrameLayout {
    
    public interface ActionClickListener {
        void onActionClick(int actionId);
    }
    
    // Action IDs
    public static final int ACTION_SEND_MESSAGE = 1;
    public static final int ACTION_CALL = 2;
    public static final int ACTION_VIDEO_CALL = 3;
    public static final int ACTION_EDIT_PROFILE = 4;
    public static final int ACTION_ADD_CONTACT = 5;
    public static final int ACTION_SHARE_CONTACT = 6;
    public static final int ACTION_BLOCK_USER = 7;
    public static final int ACTION_MUTE_NOTIFICATIONS = 8;
    public static final int ACTION_EDIT_CHAT = 9;
    public static final int ACTION_LEAVE_CHAT = 10;
    
    private LinearLayout actionsContainer;
    private List<ActionItem> actions = new ArrayList<>();
    private ActionClickListener clickListener;
    private Theme.ResourcesProvider resourcesProvider;
    
    private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF rectF = new RectF();
    
    public ModernActionCard(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        
        setWillNotDraw(false);
        createViews();
    }
    
    private void createViews() {
        // Card background
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), Theme.getColor(Theme.key_divider, resourcesProvider));
        setBackground(background);
        
        setPadding(dp(16), dp(16), dp(16), dp(16));
        
        // Actions container
        actionsContainer = new LinearLayout(getContext());
        actionsContainer.setOrientation(LinearLayout.VERTICAL);
        actionsContainer.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        
        // Create divider
        GradientDrawable divider = new GradientDrawable();
        divider.setShape(GradientDrawable.RECTANGLE);
        divider.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        divider.setSize(LayoutHelper.MATCH_PARENT, dp(1));
        actionsContainer.setDividerDrawable(divider);
        
        addView(actionsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }
    
    public void setActionClickListener(ActionClickListener listener) {
        this.clickListener = listener;
    }
    
    public void addAction(int actionId, String title, int iconRes, boolean isPrimary) {
        ActionItem item = new ActionItem();
        item.id = actionId;
        item.title = title;
        item.iconRes = iconRes;
        item.isPrimary = isPrimary;
        actions.add(item);
        
        createActionView(item);
    }
    
    public void addAction(int actionId, String title, int iconRes) {
        addAction(actionId, title, iconRes, false);
    }
    
    private void createActionView(ActionItem item) {
        FrameLayout actionView = new FrameLayout(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                
                if (item.isPrimary) {
                    // Draw primary action background
                    backgroundPaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
                    rectF.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    canvas.drawRoundRect(rectF, dp(12), dp(12), backgroundPaint);
                }
            }
        };
        actionView.setWillNotDraw(false);
        actionView.setMinimumHeight(dp(52));
        
        // Set up ripple effect
        RippleDrawable ripple = new RippleDrawable(
            ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 24),
            null, null
        );
        actionView.setBackground(ripple);
        
        // Scale animation
        ScaleStateListAnimator.apply(actionView, 0.98f, 1.02f);
        
        actionView.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (clickListener != null) {
                clickListener.onActionClick(item.id);
            }
        });
        
        // Icon
        ImageView iconView = new ImageView(getContext());
        iconView.setImageResource(item.iconRes);
        
        int iconColor = item.isPrimary ? 
            Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourcesProvider) :
            Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider);
            
        iconView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        actionView.addView(iconView, LayoutHelper.createFrame(24, 24, 
            Gravity.CENTER_VERTICAL | Gravity.LEFT, 16, 0, 0, 0));
        
        // Title
        TextView titleView = new TextView(getContext());
        titleView.setText(item.title);
        titleView.setTextSize(16);
        titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        
        int textColor = item.isPrimary ? 
            Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourcesProvider) :
            Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            
        titleView.setTextColor(textColor);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        actionView.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL | Gravity.LEFT, 56, 0, 16, 0));
        
        // Add to container
        actionsContainer.addView(actionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }
    
    public void clearActions() {
        actions.clear();
        actionsContainer.removeAllViews();
    }
    
    // Preset configurations for different profile types
    public static ModernActionCard createForUserProfile(Context context, TLRPC.User user, boolean isMyProfile, 
                                                       ActionClickListener listener, Theme.ResourcesProvider resourcesProvider) {
        ModernActionCard card = new ModernActionCard(context, resourcesProvider);
        card.setActionClickListener(listener);
        
        if (isMyProfile) {
            card.addAction(ACTION_EDIT_PROFILE, LocaleController.getString(R.string.EditProfile), 
                R.drawable.msg_edit, true);
        } else {
            card.addAction(ACTION_SEND_MESSAGE, LocaleController.getString(R.string.SendMessage), 
                R.drawable.msg_message, true);
                
            if (!user.bot) {
                card.addAction(ACTION_CALL, LocaleController.getString(R.string.Call), 
                    R.drawable.msg_call);
                card.addAction(ACTION_VIDEO_CALL, LocaleController.getString(R.string.VideoCall), 
                    R.drawable.msg_video);
            }
            
            if (!user.contact && !user.bot) {
                card.addAction(ACTION_ADD_CONTACT, LocaleController.getString(R.string.AddToContacts), 
                    R.drawable.msg_addcontact);
            }
            
            card.addAction(ACTION_SHARE_CONTACT, LocaleController.getString(R.string.ShareContact), 
                R.drawable.msg_share);
        }
        
        return card;
    }
    
    public static ModernActionCard createForChatProfile(Context context, TLRPC.Chat chat, boolean canEdit,
                                                       ActionClickListener listener, Theme.ResourcesProvider resourcesProvider) {
        ModernActionCard card = new ModernActionCard(context, resourcesProvider);
        card.setActionClickListener(listener);
        
        card.addAction(ACTION_SEND_MESSAGE, LocaleController.getString(R.string.SendMessage), 
            R.drawable.msg_message, true);
            
        if (canEdit) {
            card.addAction(ACTION_EDIT_CHAT, LocaleController.getString(R.string.Edit), 
                R.drawable.msg_edit);
        }
        
        card.addAction(ACTION_MUTE_NOTIFICATIONS, LocaleController.getString(R.string.MuteNotifications), 
            R.drawable.msg_mute);
        
        // Add leave option for groups (but not channels where user is admin)
        if (!chat.creator && chat.left == false) {
            String leaveText = chat.megagroup ? 
                LocaleController.getString(R.string.LeaveMegaMenu) : 
                LocaleController.getString(R.string.LeaveChannel);
            card.addAction(ACTION_LEAVE_CHAT, leaveText, R.drawable.msg_leave);
        }
        
        return card;
    }
    
    public static ModernActionCard createForBusinessProfile(Context context, TLRPC.User user, 
                                                           ActionClickListener listener, Theme.ResourcesProvider resourcesProvider) {
        ModernActionCard card = new ModernActionCard(context, resourcesProvider);
        card.setActionClickListener(listener);
        
        card.addAction(ACTION_SEND_MESSAGE, LocaleController.getString(R.string.SendMessage), 
            R.drawable.msg_message, true);
        card.addAction(ACTION_CALL, LocaleController.getString(R.string.Call), 
            R.drawable.msg_call);
        
        // Business-specific actions could be added here
        // e.g., "View Business Hours", "Get Directions", etc.
        
        return card;
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        
        // Add some margin to the card
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        if (params == null) {
            params = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        }
        params.setMargins(dp(16), dp(8), dp(16), dp(8));
        setLayoutParams(params);
    }
    
    private static class ActionItem {
        int id;
        String title;
        int iconRes;
        boolean isPrimary;
    }
} 