package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.URLSpanUserMention;

import java.util.ArrayList;
import java.util.List;

public class ModernInfoCard extends FrameLayout {
    
    public interface InfoClickListener {
        void onInfoClick(String type, String value);
        void onLinkClick(String url);
        void onPhoneClick(String phone);
        void onUsernameClick(String username);
    }
    
    private LinearLayout contentContainer;
    private InfoClickListener clickListener;
    private Theme.ResourcesProvider resourcesProvider;
    
    private TLRPC.User currentUser;
    private TLRPC.Chat currentChat;
    private TLRPC.UserFull userInfo;
    private TLRPC.ChatFull chatInfo;
    
    private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF rectF = new RectF();
    
    private List<InfoRow> infoRows = new ArrayList<>();
    
    public ModernInfoCard(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        
        setWillNotDraw(false);
        createViews();
    }
    
    private void createViews() {
        // Card background with modern styling
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), Theme.getColor(Theme.key_divider, resourcesProvider));
        setBackground(background);
        
        setPadding(dp(20), dp(20), dp(20), dp(20));
        
        // Content container
        contentContainer = new LinearLayout(getContext());
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        addView(contentContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }
    
    public void setInfoClickListener(InfoClickListener listener) {
        this.clickListener = listener;
    }
    
    public void setUserData(TLRPC.User user, TLRPC.UserFull userFull) {
        this.currentUser = user;
        this.userInfo = userFull;
        this.currentChat = null;
        this.chatInfo = null;
        
        updateUserInfo();
    }
    
    public void setChatData(TLRPC.Chat chat, TLRPC.ChatFull chatFull) {
        this.currentChat = chat;
        this.chatInfo = chatFull;
        this.currentUser = null;
        this.userInfo = null;
        
        updateChatInfo();
    }
    
    private void updateUserInfo() {
        clearContent();
        
        if (currentUser == null) return;
        
        // Bio/About
        if (userInfo != null && !TextUtils.isEmpty(userInfo.about)) {
            addInfoRow(LocaleController.getString(R.string.UserBio), userInfo.about, "bio", true);
        }
        
        // Username
        if (!TextUtils.isEmpty(currentUser.username)) {
            String username = "@" + currentUser.username;
            addInfoRow(LocaleController.getString(R.string.Username), username, "username", false);
        }
        
        // Phone number
        if (!TextUtils.isEmpty(currentUser.phone)) {
            String phone = "+" + currentUser.phone;
            addInfoRow(LocaleController.getString(R.string.PhoneNumber), phone, "phone", false);
        }
        
        // Birthday (if available)
        if (userInfo != null && userInfo.birthday != null) {
            String birthdayStr = formatBirthday(userInfo.birthday);
            if (!TextUtils.isEmpty(birthdayStr)) {
                addInfoRow(LocaleController.getString(R.string.Birthday), birthdayStr, "birthday", false);
            }
        }
        
        // Bot info
        if (currentUser.bot && userInfo != null && userInfo.bot_info != null && 
            !TextUtils.isEmpty(userInfo.bot_info.description)) {
            addInfoRow(LocaleController.getString(R.string.BotInfo), userInfo.bot_info.description, "bot_info", true);
        }
    }
    
    private void updateChatInfo() {
        clearContent();
        
        if (currentChat == null) return;
        
        // Description
        if (chatInfo != null && !TextUtils.isEmpty(chatInfo.about)) {
            String labelText = ChatObject.isChannel(currentChat) && !currentChat.megagroup ?
                LocaleController.getString(R.string.DescriptionPlaceholder) :
                LocaleController.getString(R.string.DescriptionPlaceholder);
            addInfoRow(labelText, chatInfo.about, "description", true);
        }
        
        // Username
        if (!TextUtils.isEmpty(currentChat.username)) {
            String username = "@" + currentChat.username;
            addInfoRow(LocaleController.getString(R.string.Username), username, "username", false);
        }
        
        // Invite link (for public chats)
        if (chatInfo != null && !TextUtils.isEmpty(chatInfo.exported_invite) && 
            chatInfo.exported_invite.link != null) {
            addInfoRow(LocaleController.getString(R.string.InviteLink), 
                chatInfo.exported_invite.link, "invite_link", false);
        }
        
        // Creation date
        if (currentChat.date > 0) {
            String dateStr = LocaleController.formatDateAudio(currentChat.date, false);
            addInfoRow(LocaleController.getString(R.string.Created), dateStr, "created", false);
        }
    }
    
    private void addInfoRow(String label, String value, String type, boolean isExpandable) {
        InfoRow row = new InfoRow();
        row.label = label;
        row.value = value;
        row.type = type;
        row.isExpandable = isExpandable;
        infoRows.add(row);
        
        createInfoRowView(row);
    }
    
    private void createInfoRowView(InfoRow row) {
        LinearLayout rowContainer = new LinearLayout(getContext());
        rowContainer.setOrientation(LinearLayout.VERTICAL);
        rowContainer.setPadding(0, infoRows.size() > 1 ? dp(16) : 0, 0, 0);
        
        // Label
        TextView labelView = new TextView(getContext());
        labelView.setText(row.label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        labelView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        labelView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        labelView.setPadding(0, 0, 0, dp(4));
        rowContainer.addView(labelView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        
        // Value
        TextView valueView = createValueTextView(row);
        rowContainer.addView(valueView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        
        contentContainer.addView(rowContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }
    
    private TextView createValueTextView(InfoRow row) {
        LinkSpanDrawable.LinksTextView valueView = new LinkSpanDrawable.LinksTextView(getContext(), resourcesProvider);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        valueView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
        
        // Handle different types of content
        SpannableStringBuilder text = new SpannableStringBuilder(row.value);
        
        switch (row.type) {
            case "phone":
                // Make phone clickable
                text.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View view) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        if (clickListener != null) {
                            clickListener.onPhoneClick(row.value);
                        }
                    }
                }, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
                
            case "username":
                // Make username clickable
                text.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View view) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        if (clickListener != null) {
                            clickListener.onUsernameClick(row.value);
                        }
                    }
                }, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
                
            case "invite_link":
                // Make invite link clickable
                text.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View view) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        if (clickListener != null) {
                            clickListener.onLinkClick(row.value);
                        }
                    }
                }, 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                break;
                
            case "bio":
            case "description":
            case "bot_info":
                // Process links in bio/description
                MessageObject.addLinks(false, text, false, false, false, true);
                break;
        }
        
        valueView.setText(text);
        valueView.setMovementMethod(LinkMovementMethod.getInstance());
        
        // Handle expandable content
        if (row.isExpandable && text.length() > 150) {
            valueView.setMaxLines(3);
            valueView.setEllipsize(TextUtils.TruncateAt.END);
            
            // Add "Show more" functionality
            valueView.setOnClickListener(v -> {
                if (valueView.getMaxLines() == 3) {
                    valueView.setMaxLines(Integer.MAX_VALUE);
                    valueView.setEllipsize(null);
                } else {
                    valueView.setMaxLines(3);
                    valueView.setEllipsize(TextUtils.TruncateAt.END);
                }
            });
        }
        
        return valueView;
    }
    
    private void clearContent() {
        infoRows.clear();
        contentContainer.removeAllViews();
    }
    
    private String formatBirthday(TLRPC.Birthday birthday) {
        if (birthday == null) return "";
        
        try {
            String monthName = LocaleController.getInstance().formatterMonthYear.format(
                new java.util.Date(0, birthday.month - 1, birthday.day)
            );
            
            if (birthday.year > 0) {
                return LocaleController.formatString("BirthdayWithYear", R.string.BirthdayWithYear, 
                    monthName, birthday.day, birthday.year);
            } else {
                return LocaleController.formatString("Birthday", R.string.Birthday, 
                    monthName, birthday.day);
            }
        } catch (Exception e) {
            return "";
        }
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        
        // Add margins to the card
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        if (params == null) {
            params = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        }
        params.setMargins(dp(16), dp(8), dp(16), dp(8));
        setLayoutParams(params);
    }
    
    // Hide card if no content
    public boolean hasContent() {
        return !infoRows.isEmpty();
    }
    
    private static class InfoRow {
        String label;
        String value;
        String type;
        boolean isExpandable;
    }
} 