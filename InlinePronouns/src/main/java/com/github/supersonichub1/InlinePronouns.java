package com.github.supersonichub1;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.aliucord.CollectionUtils;
import com.aliucord.Main;
import com.aliucord.Utils;
import com.aliucord.annotations.AliucordPlugin;
import com.aliucord.entities.MessageEmbedBuilder;
import com.aliucord.entities.Plugin;
import com.aliucord.patcher.*;
import com.aliucord.wrappers.GuildWrapper;
import com.aliucord.wrappers.embeds.MessageEmbedWrapper;
import com.discord.api.role.GuildRole;
import com.discord.models.guild.Guild;
import com.discord.models.member.GuildMember;
import com.discord.models.message.Message;
import com.discord.models.user.CoreUser;
import com.discord.stores.Store;
import com.discord.stores.StoreGuilds;
import com.discord.stores.StoreStream;
import com.discord.stores.StoreUserTyping;
import com.discord.utilities.color.ColorCompat;
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMessage;
import com.discord.widgets.chat.list.entries.ChatListEntry;
import com.discord.widgets.chat.list.entries.MessageEntry;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.lytefast.flexinput.R;

// Aliucord Plugin annotation. Must be present on the main class of your plugin
@AliucordPlugin(requiresRestart = true /* Whether your plugin requires a restart after being installed/updated */)
// Plugin class. Must extend Plugin and override start and stop
// Learn more: https://github.com/Aliucord/documentation/blob/main/plugin-dev/1_introduction.md#basic-plugin-structure
public class InlinePronouns extends Plugin {
    public final int viewId = View.generateViewId();
    private final HashSet<String> pronouns = new HashSet<>(Arrays.asList(
            "he/him",
            "she/her",
            "they/them",
            "any pronouns",
            "any",
            "ask for pronouns",
            "ask"
    ));
    private final StoreGuilds guildStore = StoreStream.getGuilds();


    @Override
    public void start(Context context) throws Throwable {
        Field itemTimestampField = WidgetChatListAdapterItemMessage.class.getDeclaredField("itemTimestamp");
        itemTimestampField.setAccessible(true);

        Field roleNameField = GuildRole.class.getDeclaredField("name");
        roleNameField.setAccessible(true);

        patcher.patch(
            WidgetChatListAdapterItemMessage.class.getDeclaredMethod("onConfigure", int.class, ChatListEntry.class),
            new Hook(param -> {
                try {

                    MessageEntry messageEntry = (MessageEntry) param.args[1];
                    Message message = messageEntry.getMessage();
                    if (message == null) {
                        Main.logger.verbose("No message.");
                        return;
                    }

                    Guild guild = guildStore.getGuild(StoreStream.getGuildSelected().getSelectedGuildId());
                    CoreUser user = new CoreUser(message.getAuthor());
                    GuildMember member = guildStore.getMember(guild.getId(), user.getId());

                    Map<Long, GuildRole> guildRoles = guildStore.getRoles().get(guild.getId());

                    String pronounRoles = member
                            .getRoles()
                            .stream()
                            .map(guildRoles::get)
                            .filter(Objects::nonNull)
                            .map(role -> {
                                try {
                                    return (String) roleNameField.get(role);
                                } catch (IllegalAccessException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .filter(pronouns::contains)
                            .collect(Collectors.joining(", "));

                    TextView itemTimestamp = (TextView) itemTimestampField.get(param.thisObject);
                    if (itemTimestamp == null) {
                        Main.logger.verbose("No item timestamp.");
                        return;
                    }

                    ConstraintLayout header = (ConstraintLayout) itemTimestamp.getParent();
                    TextView pronounsView = header.findViewById(viewId);
                    if (pronounsView == null) {
                        pronounsView = new TextView(header.getContext(), null, 0, R.i.UiKit_TextView);
                        pronounsView.setId(viewId);
                        pronounsView.setTextSize(12);
                        pronounsView.setTextColor(ColorCompat.getThemedColor(header.getContext(), R.b.colorTextMuted));
                        header.addView(pronounsView);

                        var set = new ConstraintSet();
                        set.clone(header);
                        set.constrainedHeight(viewId, true);
                        // Stop pronouns from veering off to the right
                        set.constrainedWidth(viewId, true);
                        set.connect(viewId, ConstraintSet.BASELINE, Utils.getResId("chat_list_adapter_item_text_name", "id"), ConstraintSet.BASELINE);
                        set.connect(viewId, ConstraintSet.START, itemTimestamp.getId(), ConstraintSet.END);
                        set.connect(viewId, ConstraintSet.END, header.getId(), ConstraintSet.END);
                        set.connect(itemTimestamp.getId(), ConstraintSet.END, viewId, ConstraintSet.END);
                        set.applyTo(header);
                    }

                    addPronounsToHeader(pronounsView, user.isBot(), pronounRoles);
                } catch (Throwable e) {
                    Main.logger.error(e);
                }

            })
        );
    }

    @SuppressLint("SetTextI18n")
    private void addPronounsToHeader(TextView pronounsView, boolean bot, String pronounRoles) {
        if (bot || pronounRoles.length() < 1) {
            pronounsView.setVisibility(View.GONE);
            return;
        }

        pronounsView.setVisibility(View.VISIBLE);
        pronounsView.setText(" • " + pronounRoles);
    }

    @Override
    public void stop(Context context) {
        // Remove all patches
        patcher.unpatchAll();
    }
}
