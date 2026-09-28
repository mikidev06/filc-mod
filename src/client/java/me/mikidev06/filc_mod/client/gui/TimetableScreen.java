package me.mikidev06.filc_mod.client.gui;

import java.util.ArrayList;
import java.util.List;
import me.mikidev06.filc_mod.client.FilcState;
import me.mikidev06.filc_mod.client.icon.SubjectIcons;
import me.mikidev06.filc_mod.grid.Cell;
import me.mikidev06.filc_mod.grid.Day;
import me.mikidev06.filc_mod.grid.PeriodRange;
import me.mikidev06.filc_mod.model.Lesson;
import me.mikidev06.filc_mod.model.Period;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * A week of lessons on a chest grid: rows are Monday to Friday, columns are
 * periods, and the last row holds the controls.
 */
public final class TimetableScreen extends ChestScreen {

    private static final int CONTROL_ROW = 5;
    private static final int SLOT_REFRESH = slotIndex(CONTROL_ROW, 0);
    private static final int SLOT_SUBS = slotIndex(CONTROL_ROW, 2);
    private static final int SLOT_CLOSE = slotIndex(CONTROL_ROW, 8);

    private static final int LABEL_COLOUR = 0xFF404040;
    private static final int TODAY_TINT = 0x3055FF55;

    private final TimetableView view;
    private final Day today;

    public TimetableScreen(TimetableView view) {
        super(Text.literal(view.heading()));
        this.view = view;
        this.today = Day.today(java.time.LocalDate.now());
    }

    private PeriodRange range() {
        return view.grid().range();
    }

    /** The cell a slot maps to, or null for slots outside the week. */
    private Cell cellAt(int slot) {
        int row = slot / COLUMNS;
        int column = slot % COLUMNS;
        if (row >= 5) {
            return null;
        }
        Day day = Day.ofNumber(row + 1);
        if (day == null) {
            return null;
        }
        int period = range().first() + column;
        if (!range().contains(period)) {
            return null;
        }
        return view.grid().cell(day, period);
    }

    @Override
    protected ItemStack stackFor(int slot) {
        if (slot == SLOT_REFRESH) {
            return new ItemStack(Items.CLOCK);
        }
        if (slot == SLOT_SUBS && view.cohortId() != null) {
            return new ItemStack(view.substitutions().isEmpty() ? Items.PAPER : Items.ORANGE_DYE);
        }
        if (slot == SLOT_CLOSE) {
            return new ItemStack(Items.BARRIER);
        }

        Cell cell = cellAt(slot);
        if (cell == null || cell.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Lesson primary = cell.primary();
        ItemStack stack = new ItemStack(
                SubjectIcons.forSubject(
                        primary.subject() == null ? null : primary.subject().name(),
                        primary.subjectShort()));
        if (view.grid().hasSubstitution(cell)) {
            // A glint is the cheapest way to make a changed lesson obvious
            // without stealing the slot from its subject icon.
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    @Override
    protected String countLabel(int slot) {
        Cell cell = cellAt(slot);
        if (cell == null || cell.size() <= 1) {
            return null;
        }
        return String.valueOf(cell.size());
    }

    @Override
    protected List<Text> tooltipFor(int slot) {
        if (slot == SLOT_REFRESH) {
            return List.of(
                    Text.translatable("filc_mod.ui.refresh").formatted(Formatting.WHITE),
                    Text.translatable("filc_mod.ui.refresh.desc").formatted(Formatting.DARK_GRAY));
        }
        if (slot == SLOT_SUBS && view.cohortId() != null) {
            int count = view.substitutions().size();
            return List.of(
                    Text.translatable("filc_mod.ui.substitutions").formatted(Formatting.GOLD),
                    (count == 0
                            ? Text.translatable("filc_mod.ui.substitutions.none")
                            : Text.translatable("filc_mod.ui.substitutions.count", count))
                            .formatted(Formatting.DARK_GRAY));
        }
        if (slot == SLOT_CLOSE) {
            return List.of(Text.translatable("filc_mod.ui.close").formatted(Formatting.RED));
        }

        Cell cell = cellAt(slot);
        if (cell == null || cell.isEmpty()) {
            return List.of();
        }
        return lessonTooltip(cell);
    }

    private List<Text> lessonTooltip(Cell cell) {
        List<Text> lines = new ArrayList<>();
        Period period = view.periods().get(cell.period());
        String time = period == null ? "" : period.startShort() + " - " + period.endShort();

        lines.add(Text.translatable("filc_mod.ui.period",
                        Text.translatable(cell.day().translationKey()), cell.period())
                .formatted(Formatting.WHITE));
        if (!time.isEmpty()) {
            lines.add(Text.literal(time).formatted(Formatting.GRAY));
        }
        lines.add(Text.empty());

        for (Lesson lesson : cell.lessons()) {
            boolean substituted = view.grid().isSubstituted(lesson);
            StringBuilder head = new StringBuilder(lesson.subjectShort());
            String group = lesson.groupLabel();
            if (!group.isEmpty()) {
                head.append(" [").append(group).append("]");
            }
            lines.add(Text.literal(head.toString())
                    .formatted(substituted ? Formatting.GOLD : Formatting.YELLOW));
            String week = lesson.weekLabel();
            if (!week.isEmpty()) {
                lines.add(Text.translatable("filc_mod.ui.week", week).formatted(Formatting.AQUA));
            }

            String teachers = lesson.teacherNames();
            String rooms = lesson.roomNames();
            if (!teachers.isEmpty()) {
                lines.add(Text.literal("  " + teachers).formatted(Formatting.GRAY));
            }
            if (!rooms.isEmpty()) {
                lines.add(Text.literal("  " + rooms).formatted(Formatting.DARK_GRAY));
            }
            if (substituted) {
                lines.add(Text.translatable("filc_mod.ui.substituted").formatted(Formatting.RED));
            }
        }

        if (cell.size() > 1) {
            lines.add(Text.empty());
            lines.add(Text.translatable("filc_mod.ui.parallel", cell.size())
                    .formatted(Formatting.DARK_GRAY));
        }
        return lines;
    }

    @Override
    protected void onSlotClick(int slot) {
        if (slot == SLOT_CLOSE) {
            this.close();
            return;
        }
        if (slot == SLOT_REFRESH) {
            FilcState.get().clearCaches();
            this.close();
            sendMessage(Text.translatable("filc_mod.msg.cache_cleared_rerun")
                    .formatted(Formatting.GRAY));
            return;
        }
        if (slot == SLOT_SUBS && view.cohortId() != null) {
            FilcState.get().openLater(new SubstitutionsScreen(view));
            return;
        }

        Cell cell = cellAt(slot);
        if (cell == null || cell.isEmpty()) {
            return;
        }
        this.close();
        for (Text line : lessonTooltip(cell)) {
            sendMessage(line);
        }
    }

    private void sendMessage(Text text) {
        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(text, false);
        }
    }

    @Override
    protected void renderOverlay(DrawContext context, int mouseX, int mouseY) {
        // Heading above the chest, so the title strip can carry period numbers.
        String heading = view.heading()
                + (view.groupFilter() == null ? "" : " [" + view.groupFilter() + "]");
        context.drawTextWithShadow(this.textRenderer, Text.literal(heading),
                backgroundX, backgroundY - 22, 0xFFFFFFFF);
        if (view.subheading() != null) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(view.subheading()),
                    backgroundX, backgroundY - 12, 0xFFA0A0A0);
        }

        // Period numbers in the chest's title strip.
        PeriodRange range = range();
        for (int column = 0; column < COLUMNS; column++) {
            int period = range.first() + column;
            if (!range.contains(period)) {
                continue;
            }
            String label = String.valueOf(period);
            int centre = slotX(column) + 8;
            context.drawText(this.textRenderer, label,
                    centre - this.textRenderer.getWidth(label) / 2,
                    backgroundY + 6, LABEL_COLOUR, false);
        }

        // Day names to the left of the chest, and a tint on today's row.
        for (Day day : Day.values()) {
            int rowSlot = slotIndex(day.row(), 0);
            int y = slotY(rowSlot) + 4;
            boolean isToday = day == today;
            context.drawTextWithShadow(this.textRenderer,
                    Text.translatable(day.shortTranslationKey()),
                    backgroundX - 22, y, isToday ? 0xFF55FF55 : 0xFFBBBBBB);
            if (isToday) {
                context.fill(
                        slotX(rowSlot) - 1,
                        slotY(rowSlot) - 1,
                        slotX(rowSlot) + COLUMNS * SLOT_SIZE - 1,
                        slotY(rowSlot) + SLOT_SIZE - 1,
                        TODAY_TINT);
            }
        }

        if (view.grid().isEmpty()) {
            Text empty = Text.translatable("filc_mod.ui.no_lessons").formatted(Formatting.GRAY);
            context.drawTextWithShadow(this.textRenderer, empty,
                    backgroundX + BACKGROUND_WIDTH / 2 - this.textRenderer.getWidth(empty) / 2,
                    backgroundY + 54, 0xFFFFFFFF);
        }
    }
}
