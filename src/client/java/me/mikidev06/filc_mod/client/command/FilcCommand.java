package me.mikidev06.filc_mod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import me.mikidev06.filc_mod.api.FilcApiException;
import me.mikidev06.filc_mod.api.FilcClient;
import me.mikidev06.filc_mod.client.FilcState;
import me.mikidev06.filc_mod.client.gui.TimetableScreen;
import me.mikidev06.filc_mod.client.gui.TimetableView;
import me.mikidev06.filc_mod.grid.WeekGrid;
import me.mikidev06.filc_mod.model.Classroom;
import me.mikidev06.filc_mod.model.Cohort;
import me.mikidev06.filc_mod.model.Lesson;
import me.mikidev06.filc_mod.model.Period;
import me.mikidev06.filc_mod.model.SubstitutionEntry;
import me.mikidev06.filc_mod.model.Teacher;
import me.mikidev06.filc_mod.model.Timetable;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Registers and runs {@code /filc}. */
public final class FilcCommand {

    private FilcCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("filc")
                .executes(ctx -> defaultTimetable(ctx.getSource()))

                .then(ClientCommandManager.literal("class")
                        .then(ClientCommandManager.argument("cohort", StringArgumentType.word())
                                .suggests(cohortSuggestions())
                                .executes(ctx -> openCohort(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "cohort"),
                                        null))
                                .then(ClientCommandManager.argument("group", StringArgumentType.word())
                                        .suggests(groupSuggestions())
                                        .executes(ctx -> openCohort(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "cohort"),
                                                StringArgumentType.getString(ctx, "group"))))))

                .then(ClientCommandManager.literal("teacher")
                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                .suggests(teacherSuggestions())
                                .executes(ctx -> openTeacher(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))

                .then(ClientCommandManager.literal("room")
                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                .suggests(roomSuggestions())
                                .executes(ctx -> openRoom(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))

                .then(ClientCommandManager.literal("subs")
                        .executes(ctx -> openSubs(ctx.getSource(), null))
                        .then(ClientCommandManager.argument("cohort", StringArgumentType.word())
                                .suggests(cohortSuggestions())
                                .executes(ctx -> openSubs(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "cohort")))))

                .then(ClientCommandManager.literal("setclass")
                        .then(ClientCommandManager.argument("cohort", StringArgumentType.word())
                                .suggests(cohortSuggestions())
                                .executes(ctx -> setDefault(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "cohort"),
                                        null))
                                .then(ClientCommandManager.argument("group", StringArgumentType.word())
                                        .suggests(groupSuggestions())
                                        .executes(ctx -> setDefault(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "cohort"),
                                                StringArgumentType.getString(ctx, "group"))))))

                .then(ClientCommandManager.literal("refresh")
                        .executes(ctx -> refresh(ctx.getSource()))));
    }

    // ---- completion ------------------------------------------------------

    private static SuggestionProvider<FabricClientCommandSource> cohortSuggestions() {
        return (ctx, builder) -> CommandSource.suggestMatching(
                FilcState.get().cohorts().stream().map(Cohort::display), builder);
    }

    private static SuggestionProvider<FabricClientCommandSource> teacherSuggestions() {
        return (ctx, builder) -> CommandSource.suggestMatching(
                FilcState.get().teachers().stream()
                        .map(t -> t.shortName() == null ? t.displayName() : t.shortName()),
                builder);
    }

    private static SuggestionProvider<FabricClientCommandSource> roomSuggestions() {
        return (ctx, builder) -> CommandSource.suggestMatching(
                FilcState.get().classrooms().stream().map(Classroom::display), builder);
    }

    /**
     * Group names are not known until a class is picked, and completion cannot
     * wait on a fetch, so this offers the divisions the live data actually uses.
     */
    private static SuggestionProvider<FabricClientCommandSource> groupSuggestions() {
        return (ctx, builder) -> CommandSource.suggestMatching(
                Stream.of("gy1", "gy2", "info1", "info2", "n1", "n2"), builder);
    }

    // ---- commands --------------------------------------------------------

    private static int defaultTimetable(FabricClientCommandSource source) {
        FilcState state = FilcState.get();
        if (!state.config().hasDefaultCohort()) {
            source.sendFeedback(prefixed(Text.translatable("filc_mod.msg.no_default"))
                    .formatted(Formatting.YELLOW));
            for (String key : List.of("filc_mod.help.setclass", "filc_mod.help.class",
                    "filc_mod.help.teacher", "filc_mod.help.room", "filc_mod.help.subs")) {
                source.sendFeedback(Text.literal("  ")
                        .append(Text.translatable(key)).formatted(Formatting.GRAY));
            }
            return 0;
        }
        return openCohort(source, state.config().defaultCohort(), state.config().defaultGroup());
    }

    private static int openCohort(FabricClientCommandSource source, String cohortName, String group) {
        MinecraftClient minecraft = source.getClient();
        FilcState state = FilcState.get();
        info(minecraft, Text.translatable("filc_mod.msg.loading", cohortName));

        state.loadCohorts()
                .thenCompose(cohorts -> {
                    Cohort cohort = findCohort(cohorts, cohortName);
                    if (cohort == null) {
                        return CompletableFuture.failedFuture(
                                new UnknownTarget("filc_mod.msg.unknown_class", cohortName));
                    }
                    return buildCohortView(state, cohort, group);
                })
                .thenAccept(view -> minecraft.execute(() -> state.openLater(new TimetableScreen(view))))
                .exceptionally(error -> report(minecraft, error));
        return 1;
    }

    private static CompletableFuture<TimetableView> buildCohortView(
            FilcState state, Cohort cohort, String group) {
        return state.currentTimetable().thenCompose(timetable -> {
            String timetableId = timetable == null ? null : timetable.id();
            CompletableFuture<List<Lesson>> lessons =
                    state.client().lessonsForCohort(cohort.id(), timetableId);
            CompletableFuture<List<Period>> periods = timetableId == null
                    ? CompletableFuture.completedFuture(List.<Period>of())
                    : state.client().periods(timetableId);
            // Substitutions are the one volatile piece; a failure there should
            // not cost the player their timetable.
            CompletableFuture<List<SubstitutionEntry>> subs =
                    state.client().substitutionsForCohort(cohort.id())
                            .exceptionally(error -> List.of());

            return CompletableFuture.allOf(lessons, periods, subs).thenApply(ignored -> view(
                    cohort.display(),
                    timetable == null ? null : timetable.name(),
                    lessons.join(),
                    periods.join(),
                    cohort.id(),
                    group,
                    subs.join()));
        });
    }

    private static int openTeacher(FabricClientCommandSource source, String name) {
        MinecraftClient minecraft = source.getClient();
        FilcState state = FilcState.get();
        info(minecraft, Text.translatable("filc_mod.msg.loading", name));

        state.loadTeachers()
                .thenCompose(teachers -> {
                    Teacher teacher = findTeacher(teachers, name);
                    if (teacher == null) {
                        return CompletableFuture.failedFuture(
                                new UnknownTarget("filc_mod.msg.unknown_teacher", name));
                    }
                    return lessonView(state, teacher.displayName(),
                            id -> state.client().lessonsForTeacher(teacher.id(), id));
                })
                .thenAccept(view -> minecraft.execute(() -> state.openLater(new TimetableScreen(view))))
                .exceptionally(error -> report(minecraft, error));
        return 1;
    }

    private static int openRoom(FabricClientCommandSource source, String name) {
        MinecraftClient minecraft = source.getClient();
        FilcState state = FilcState.get();
        info(minecraft, Text.translatable("filc_mod.msg.loading", name));

        state.loadClassrooms()
                .thenCompose(rooms -> {
                    Classroom room = findRoom(rooms, name);
                    if (room == null) {
                        return CompletableFuture.failedFuture(
                                new UnknownTarget("filc_mod.msg.unknown_room", name));
                    }
                    return lessonView(state, room.display(),
                            id -> state.client().lessonsForRoom(room.id(), id));
                })
                .thenAccept(view -> minecraft.execute(() -> state.openLater(new TimetableScreen(view))))
                .exceptionally(error -> report(minecraft, error));
        return 1;
    }

    /** Builds a view for a teacher or room, which have no substitutions of their own. */
    private static CompletableFuture<TimetableView> lessonView(
            FilcState state,
            String heading,
            java.util.function.Function<String, CompletableFuture<List<Lesson>>> fetch) {
        return state.currentTimetable().thenCompose(timetable -> {
            String timetableId = timetable == null ? null : timetable.id();
            CompletableFuture<List<Lesson>> lessons = fetch.apply(timetableId);
            CompletableFuture<List<Period>> periods = timetableId == null
                    ? CompletableFuture.completedFuture(List.<Period>of())
                    : state.client().periods(timetableId);
            return CompletableFuture.allOf(lessons, periods).thenApply(ignored -> view(
                    heading,
                    timetable == null ? null : timetable.name(),
                    lessons.join(),
                    periods.join(),
                    null,
                    null,
                    List.of()));
        });
    }

    private static int openSubs(FabricClientCommandSource source, String cohortName) {
        FilcState state = FilcState.get();
        String target = cohortName != null ? cohortName : state.config().defaultCohort();
        if (target == null || target.isBlank()) {
            source.sendError(prefixed(Text.translatable("filc_mod.msg.need_class")));
            return 0;
        }
        MinecraftClient minecraft = source.getClient();
        info(minecraft, Text.translatable("filc_mod.msg.loading", target));

        String resolved = target;
        state.loadCohorts()
                .thenCompose(cohorts -> {
                    Cohort cohort = findCohort(cohorts, resolved);
                    if (cohort == null) {
                        return CompletableFuture.failedFuture(
                                new UnknownTarget("filc_mod.msg.unknown_class", resolved));
                    }
                    return buildCohortView(state, cohort, state.config().defaultGroup());
                })
                .thenAccept(view -> minecraft.execute(() -> state.openLater(
                        new me.mikidev06.filc_mod.client.gui.SubstitutionsScreen(view))))
                .exceptionally(error -> report(minecraft, error));
        return 1;
    }

    private static int setDefault(FabricClientCommandSource source, String cohortName, String group) {
        FilcState state = FilcState.get();
        state.config().setDefaultCohort(cohortName);
        state.config().setDefaultGroup(group);
        state.saveConfig();
        source.sendFeedback(prefixed(Text.translatable("filc_mod.msg.default_set",
                cohortName + (group == null ? "" : " [" + group + "]"))).formatted(Formatting.GREEN));
        return 1;
    }

    private static int refresh(FabricClientCommandSource source) {
        FilcState.get().clearCaches();
        source.sendFeedback(prefixed(Text.translatable("filc_mod.msg.cache_cleared"))
                .formatted(Formatting.GREEN));
        return 1;
    }

    // ---- helpers ---------------------------------------------------------

    private static TimetableView view(
            String heading,
            String subheading,
            List<Lesson> lessons,
            List<Period> periods,
            String cohortId,
            String group,
            List<SubstitutionEntry> substitutions) {

        Set<String> substituted = new HashSet<>();
        for (SubstitutionEntry entry : substitutions) {
            substituted.addAll(entry.lessonIds());
        }

        Map<Integer, Period> byNumber = new LinkedHashMap<>();
        for (Period period : periods) {
            byNumber.put(period.number(), period);
        }

        Map<String, Lesson> byId = new HashMap<>();
        for (Lesson lesson : lessons) {
            if (lesson.id() != null) {
                byId.put(lesson.id(), lesson);
            }
        }

        return new TimetableView(
                heading,
                subheading,
                WeekGrid.build(lessons, group, substituted),
                byNumber,
                cohortId,
                group,
                new ArrayList<>(substitutions),
                byId);
    }

    static Cohort findCohort(List<Cohort> cohorts, String name) {
        String needle = name.trim().toLowerCase(Locale.ROOT);
        for (Cohort cohort : cohorts) {
            if (matches(cohort.shortName(), needle) || matches(cohort.name(), needle)) {
                return cohort;
            }
        }
        return null;
    }

    static Teacher findTeacher(List<Teacher> teachers, String name) {
        String needle = name.trim().toLowerCase(Locale.ROOT);
        for (Teacher teacher : teachers) {
            if (matches(teacher.shortName(), needle) || matches(teacher.displayName(), needle)) {
                return teacher;
            }
        }
        // Fall back to a prefix match so a surname alone finds someone.
        for (Teacher teacher : teachers) {
            String display = teacher.displayName();
            if (display != null && display.toLowerCase(Locale.ROOT).startsWith(needle)) {
                return teacher;
            }
        }
        return null;
    }

    static Classroom findRoom(List<Classroom> rooms, String name) {
        String needle = name.trim().toLowerCase(Locale.ROOT);
        for (Classroom room : rooms) {
            if (matches(room.shortName(), needle) || matches(room.name(), needle)) {
                return room;
            }
        }
        for (Classroom room : rooms) {
            if (room.name() != null && room.name().toLowerCase(Locale.ROOT).startsWith(needle)) {
                return room;
            }
        }
        return null;
    }

    private static boolean matches(String candidate, String needle) {
        return candidate != null && candidate.toLowerCase(Locale.ROOT).equals(needle);
    }

    /** A target the player named that does not exist, carrying its own message. */
    private static final class UnknownTarget extends RuntimeException {
        private final String key;
        private final String name;

        UnknownTarget(String key, String name) {
            super(key);
            this.key = key;
            this.name = name;
        }

        MutableText describe() {
            return Text.translatable(key, name);
        }
    }

    private static MutableText prefixed(Text body) {
        return Text.literal("[Filc] ").append(body);
    }

    private static void info(MinecraftClient minecraft, Text message) {
        chat(minecraft, prefixed(message).formatted(Formatting.GRAY));
    }

    private static Void report(MinecraftClient minecraft, Throwable error) {
        Throwable cause = FilcClient.unwrapCause(error);
        MutableText body;
        if (cause instanceof UnknownTarget unknown) {
            body = unknown.describe();
        } else if (cause instanceof FilcApiException api && api.isRateLimited()) {
            long wait = api.retryAfterSeconds();
            body = wait > 0
                    ? Text.translatable("filc_mod.msg.rate_limited", wait)
                    : Text.translatable("filc_mod.msg.rate_limited_soon");
        } else {
            body = Text.literal(FilcClient.describe(error));
        }
        chat(minecraft, prefixed(body).formatted(Formatting.RED));
        return null;
    }

    private static void chat(MinecraftClient minecraft, Text text) {
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.sendMessage(text, false);
            }
        });
    }
}
