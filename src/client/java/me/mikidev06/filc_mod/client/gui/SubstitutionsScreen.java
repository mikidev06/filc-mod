package me.mikidev06.filc_mod.client.gui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import me.mikidev06.filc_mod.client.FilcState;
import me.mikidev06.filc_mod.grid.Day;
import me.mikidev06.filc_mod.model.Lesson;
import me.mikidev06.filc_mod.model.Period;
import me.mikidev06.filc_mod.model.SubstitutionEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The substitutions affecting a class, one item per substitution, filled left
 * to right. The first five rows hold up to 45 entries, which is far more than
 * the school ever publishes at once.
 */
public final class SubstitutionsScreen extends ChestScreen {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int CONTROL_ROW = 5;
    private static final int SLOT_BACK = slotIndex(CONTROL_ROW, 0);
    private static final int SLOT_CLOSE = slotIndex(CONTROL_ROW, 8);
    private static final int ENTRY_SLOTS = CONTROL_ROW * COLUMNS;

    private final TimetableView view;
    private final List<SubstitutionEntry> entries;

    public SubstitutionsScreen(TimetableView view) {
        super(Text.translatable("filc_mod.ui.substitutions.title", view.heading()));
        this.view = view;
        this.entries = new ArrayList<>(view.substitutions());
        this.entries.sort((a, b) -> {
            LocalDate left = a.substitution() == null ? null : a.substitution().date();
            LocalDate right = b.substitution() == null ? null : b.substitution().date();
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return 1;
            }
            if (right == null) {
                return -1;
            }
            return left.compareTo(right);
        });
    }

    private SubstitutionEntry entryAt(int slot) {
        if (slot < 0 || slot >= ENTRY_SLOTS || slot >= entries.size()) {
            return null;
        }
        return entries.get(slot);
    }

    @Override
    protected ItemStack stackFor(int slot) {
        if (slot == SLOT_BACK) {
            return new ItemStack(Items.ARROW);
        }
        if (slot == SLOT_CLOSE) {
            return new ItemStack(Items.BARRIER);
        }
        SubstitutionEntry entry = entryAt(slot);
        if (entry == null) {
            return ItemStack.EMPTY;
        }
        LocalDate date = entry.substitution() == null ? null : entry.substitution().date();
        boolean isToday = date != null && date.equals(LocalDate.now());
        return new ItemStack(isToday ? Items.ORANGE_DYE : Items.PAPER);
    }

    @Override
    protected List<Text> tooltipFor(int slot) {
        if (slot == SLOT_BACK) {
            return List.of(Text.translatable("filc_mod.ui.back").formatted(Formatting.WHITE));
        }
        if (slot == SLOT_CLOSE) {
            return List.of(Text.translatable("filc_mod.ui.close").formatted(Formatting.RED));
        }
        SubstitutionEntry entry = entryAt(slot);
        return entry == null ? List.of() : describe(entry);
    }

    private List<Text> describe(SubstitutionEntry entry) {
        List<Text> lines = new ArrayList<>();
        LocalDate date = entry.substitution() == null ? null : entry.substitution().date();
        if (date != null) {
            Day day = Day.today(date);
            MutableText line = Text.literal(DATE.format(date));
            if (day != null) {
                line.append(Text.literal(" ")).append(Text.translatable(day.translationKey()));
            }
            lines.add(line.formatted(Formatting.WHITE));
        }
        if (entry.teacher() != null) {
            lines.add(Text.translatable("filc_mod.ui.covered_by", entry.teacher().displayName())
                    .formatted(Formatting.GOLD));
        }

        for (String lessonId : entry.lessonIds()) {
            Lesson lesson = view.lessonsById().get(lessonId);
            if (lesson == null) {
                continue;
            }
            Period period = view.periods().get(lesson.periodNumber());
            String time = period == null ? "" : " " + period.startShort();
            lines.add(Text.literal("  ")
                    .append(Text.translatable("filc_mod.ui.period_short", lesson.periodNumber()))
                    .append(Text.literal(time + " - " + lesson.subjectShort()))
                    .formatted(Formatting.YELLOW));
            if (!lesson.roomNames().isEmpty()) {
                lines.add(Text.literal("    " + lesson.roomNames()).formatted(Formatting.DARK_GRAY));
            }
        }

        String comment = entry.substitution() == null ? null : entry.substitution().comment();
        if (comment != null && !comment.isBlank()) {
            lines.add(Text.empty());
            lines.add(Text.literal(comment).formatted(Formatting.GRAY));
        }
        return lines;
    }

    @Override
    protected void onSlotClick(int slot) {
        if (slot == SLOT_CLOSE) {
            this.close();
            return;
        }
        if (slot == SLOT_BACK) {
            FilcState.get().openLater(new TimetableScreen(view));
            return;
        }
        SubstitutionEntry entry = entryAt(slot);
        if (entry == null) {
            return;
        }
        this.close();
        if (this.client != null && this.client.player != null) {
            for (Text line : describe(entry)) {
                this.client.player.sendMessage(line, false);
            }
        }
    }

    @Override
    protected void renderOverlay(DrawContext context, int mouseX, int mouseY) {
        context.drawTextWithShadow(this.textRenderer, this.title,
                backgroundX, backgroundY - 12, 0xFFFFFFFF);
        if (entries.isEmpty()) {
            Text empty = Text.translatable("filc_mod.ui.no_substitutions")
                    .formatted(Formatting.GRAY);
            context.drawTextWithShadow(this.textRenderer, empty,
                    backgroundX + BACKGROUND_WIDTH / 2 - this.textRenderer.getWidth(empty) / 2,
                    backgroundY + 54, 0xFFFFFFFF);
        }
    }
}
