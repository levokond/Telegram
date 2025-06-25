package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

public class ModernProfileHeaderCell extends FrameLayout {
    
    private BackupImageView avatarImageView;
    private TextView nameTextView;
    private TextView statusTextView;
    private ImageView verifiedIconView;
    private ImageView premiumIconView;
    private FrameLayout statusContainer;
    private View statusBackground;
    
    private TLRPC.User currentUser;
    private TLRPC.Chat currentChat;
    private TLRPC.UserFull userInfo;
    private TLRPC.ChatFull chatInfo;
    
    private boolean isOnline;
    private int currentAccount;
    private Theme.ResourcesProvider resourcesProvider;
    
    private Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF rectF = new RectF();
    
    private ValueAnimator pulseAnimator;
    private float pulseProgress = 0f;
    
    public ModernProfileHeaderCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        
        setWillNotDraw(false);
        createViews();
        setupAnimations();
    }
    
    private void createViews() {
        setPadding(dp(20), dp(24), dp(20), dp(24));
        
        // Avatar container with modern styling
        FrameLayout avatarContainer = new FrameLayout(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                
                // Draw subtle glow effect for online users
                if (isOnline && pulseProgress > 0) {
                    float radius = getMeasuredWidth() / 2f;
                    float glowRadius = radius + dp(8) * pulseProgress;
                    
                    shadowPaint.setColor(ColorUtils.setAlphaComponent(
                        Theme.getColor(Theme.key_avatar_backgroundGreen, resourcesProvider), 
                        (int) (50 * pulseProgress)));
                    shadowPaint.setShadowLayer(dp(12), 0, 0, shadowPaint.getColor());
                    
                    canvas.drawCircle(getMeasuredWidth() / 2f, getMeasuredHeight() / 2f, 
                        glowRadius, shadowPaint);
                }
            }
        };
        avatarContainer.setWillNotDraw(false);
        
        // Avatar image with enhanced styling
        avatarImageView = new BackupImageView(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                // Draw modern shadow
                shadowPaint.setShadowLayer(dp(16), 0, dp(4), 
                    ColorUtils.setAlphaComponent(Color.BLACK, 30));
                shadowPaint.setColor(Color.TRANSPARENT);
                
                float radius = getMeasuredWidth() / 2f;
                canvas.drawCircle(getMeasuredWidth() / 2f, getMeasuredHeight() / 2f, 
                    radius - dp(2), shadowPaint);
                
                super.onDraw(canvas);
            }
        };
        avatarImageView.setRoundRadius(dp(60));
        ScaleStateListAnimator.apply(avatarImageView, 0.95f, 1.05f);
        
        avatarContainer.addView(avatarImageView, LayoutHelper.createFrame(120, 120, Gravity.CENTER));
        addView(avatarContainer, LayoutHelper.createFrame(120, 120, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, 16));
        
        // Name with enhanced typography
        nameTextView = new TextView(getContext());
        nameTextView.setTextSize(26);
        nameTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        nameTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        nameTextView.setMaxLines(2);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 
            Gravity.CENTER_HORIZONTAL, 20, 136, 20, 4));
        
        // Status container with modern badge styling
        statusContainer = new FrameLayout(getContext());
        
        statusBackground = new View(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                
                // Draw modern status background
                backgroundPaint.setColor(Theme.blendOver(
                    Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider),
                    Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider), 0.1f)
                ));
                
                rectF.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                canvas.drawRoundRect(rectF, dp(12), dp(12), backgroundPaint);
            }
        };
        statusBackground.setWillNotDraw(false);
        statusContainer.addView(statusBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        
        statusTextView = new TextView(getContext());
        statusTextView.setTextSize(16);
        statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        statusTextView.setGravity(Gravity.CENTER);
        statusTextView.setMaxLines(1);
        statusTextView.setEllipsize(TextUtils.TruncateAt.END);
        statusTextView.setPadding(dp(12), dp(6), dp(12), dp(6));
        statusContainer.addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        
        addView(statusContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 
            Gravity.CENTER_HORIZONTAL, 0, 170, 0, 0));
        
        // Verified icon
        verifiedIconView = new ImageView(getContext());
        verifiedIconView.setImageResource(R.drawable.verified_area);
        verifiedIconView.setColorFilter(new PorterDuffColorFilter(
            Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider), 
            PorterDuff.Mode.SRC_IN));
        verifiedIconView.setVisibility(GONE);
        
        // Premium icon  
        premiumIconView = new ImageView(getContext());
        premiumIconView.setImageResource(R.drawable.msg_premium_liststar);
        premiumIconView.setColorFilter(new PorterDuffColorFilter(
            Theme.getColor(Theme.key_profile_verifiedBackground, resourcesProvider), 
            PorterDuff.Mode.SRC_IN));
        premiumIconView.setVisibility(GONE);
    }
    
    private void setupAnimations() {
        // Pulse animation for online status
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f, 0f);
        pulseAnimator.setDuration(2000);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.addUpdateListener(animation -> {
            pulseProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
    }
    
    public void setUser(TLRPC.User user, TLRPC.UserFull userFull, int account) {
        this.currentUser = user;
        this.userInfo = userFull;
        this.currentAccount = account;
        this.currentChat = null;
        this.chatInfo = null;
        
        updateUserData();
    }
    
    public void setChat(TLRPC.Chat chat, TLRPC.ChatFull chatFull, int account) {
        this.currentChat = chat;
        this.chatInfo = chatFull;
        this.currentAccount = account;
        this.currentUser = null;
        this.userInfo = null;
        
        updateChatData();
    }
    
    private void updateUserData() {
        if (currentUser == null) {
            return;
        }
        
        // Set avatar
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(currentUser);
        avatarImageView.setForUserOrChat(currentUser, avatarDrawable);
        
        // Set name
        nameTextView.setText(UserObject.getUserName(currentUser));
        
        // Set status
        String status = "";
        isOnline = false;
        
        if (currentUser.bot) {
            status = LocaleController.getString(R.string.Bot);
        } else if (currentUser.id == UserObject.getSelfId()) {
            status = LocaleController.getString(R.string.ChatYourSelf);
        } else {
            status = LocaleController.formatUserStatus(currentAccount, currentUser, false);
            isOnline = !currentUser.bot && (currentUser.status != null && 
                currentUser.status instanceof TLRPC.TL_userStatusOnline);
        }
        
        statusTextView.setText(status);
        
        // Handle verified/premium status
        verifiedIconView.setVisibility(currentUser.verified ? VISIBLE : GONE);
        premiumIconView.setVisibility(currentUser.premium ? VISIBLE : GONE);
        
        // Start/stop pulse animation based on online status
        if (isOnline && pulseAnimator != null && !pulseAnimator.isRunning()) {
            pulseAnimator.start();
        } else if (!isOnline && pulseAnimator != null && pulseAnimator.isRunning()) {
            pulseAnimator.cancel();
            pulseProgress = 0f;
            invalidate();
        }
    }
    
    private void updateChatData() {
        if (currentChat == null) {
            return;
        }
        
        // Set avatar
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(currentChat);
        avatarImageView.setForUserOrChat(currentChat, avatarDrawable);
        
        // Set name
        nameTextView.setText(currentChat.title);
        
        // Set status
        String status = "";
        if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
            int count = chatInfo != null ? chatInfo.participants_count : 0;
            status = LocaleController.formatPluralString("Subscribers", count);
        } else {
            int count = chatInfo != null ? chatInfo.participants_count : 0;
            status = LocaleController.formatPluralString("Members", count);
        }
        
        statusTextView.setText(status);
        
        // Handle verified status for channels
        verifiedIconView.setVisibility(currentChat.verified ? VISIBLE : GONE);
        premiumIconView.setVisibility(GONE); // Chats don't have premium status
        
        // Stop pulse animation for chats
        isOnline = false;
        if (pulseAnimator != null && pulseAnimator.isRunning()) {
            pulseAnimator.cancel();
            pulseProgress = 0f;
            invalidate();
        }
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(220), MeasureSpec.EXACTLY));
    }
} 