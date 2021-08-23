package de.eldoria.companies.commands.company;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.ArgumentUtils;
import de.eldoria.eldoutilities.utils.EnumUtil;
import de.eldoria.eldoutilities.utils.Parser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Top extends EldoCommand {
    private static final int PAGE_SIZE = 15;
    private static final TopOrder DEFAULT_ORDER = TopOrder.ORDERS;
    private final MiniMessage miniMessage;
    private final BukkitAudiences audiences;
    private final ACompanyData companyData;

    public Top(Plugin plugin, ACompanyData companyData) {
        super(plugin);
        this.companyData = companyData;
        miniMessage = MiniMessage.get();
        audiences = BukkitAudiences.create(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            renderPage(sender, 1, DEFAULT_ORDER);
            return true;
        }

        var page = Parser.parseInt(args[0]);
        if (page.isEmpty()) {
            messageSender().sendError(sender, "Invalid number");
            return true;
        }

        var order = ArgumentUtils.getOptionalParameter(args, 1, DEFAULT_ORDER, s -> EnumUtil.parse(s, TopOrder.class));

        renderPage(sender, page.getAsInt(), order);
        return true;
    }

    private void renderPage(CommandSender sender, int page, TopOrder orders) {
        companyData.retrieveRanking(orders, page, PAGE_SIZE)
                .whenComplete(companyRanks -> sendPage(sender, page, orders, companyRanks));
    }

    private void sendPage(CommandSender sender, int page, TopOrder order, List<CompanyRank> ranks) {
        var composer = MessageComposer.create().localeCode("Company Ranking").newLine()
                .localeCode("Order: ");
        for (var value : TopOrder.values()) {
            composer.text("%s<click:run_command:/company top %s %s>[", order == value ? "<green>" : "<gray>", page, value.name()).localeCode(value.name()).text("]</click>");
        }
        composer.text("<reset>").newLine();
        for (var rank : ranks) {
            composer.text("%s | <hover:show_text:'%s'>%s</hover>", rank.rank(), rank.asComponent(), rank.name()).newLine();
        }
        if (page > 1) {
            composer.text("<click:run_command:/company top %s %s><white>%s/white></click>", page - 1, order.name(), Texts.LEFT_ARROW);
        } else {
            composer.text("<gray>%s<reset>", Texts.LEFT_ARROW);
        }
        composer.localeCode("Page").text(" %s ", page);

        if (ranks.size() < PAGE_SIZE) {
            composer.text("<gray>%s<reset>",Texts.RIGHT_ARROW);
        } else {
            composer.text("<click:run_command:/company top %s %s><white>%s</white></click>", page + 1, order.name(), Texts.RIGHT_ARROW);
        }
        audiences.sender(sender).sendMessage(miniMessage.parse(localizer().localize(composer.build())));
    }
}
