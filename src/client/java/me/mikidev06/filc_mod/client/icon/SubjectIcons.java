package me.mikidev06.filc_mod.client.icon;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

/**
 * Picks an item to stand for a subject.
 *
 * <p>The school publishes 137 subjects, far too many to map individually, but
 * they fall into a small number of families whose Hungarian names share a stem.
 * Matching on those stems covers the timetable of any class; anything
 * unrecognised falls back to paper, which reads as "a lesson" without implying
 * anything wrong.
 *
 * <p>Order matters: the first matching stem wins, so more specific stems are
 * listed before the general ones they would otherwise be swallowed by.
 */
public final class SubjectIcons {

    private static final Map<String, Item> RULES = new LinkedHashMap<>();

    static {
        // Substitutions and free periods first - they are not really subjects.
        RULES.put("helyettesit", Items.ORANGE_DYE);
        RULES.put("osztalyfonoki", Items.OAK_SIGN);
        RULES.put("ofo", Items.OAK_SIGN);

        // Sciences.
        RULES.put("kemia", Items.BREWING_STAND);
        RULES.put("analitik", Items.GLASS_BOTTLE);
        RULES.put("biotech", Items.FERMENTED_SPIDER_EYE);
        RULES.put("gyogysz", Items.POTION);
        RULES.put("fizika", Items.REDSTONE);
        RULES.put("biolog", Items.WHEAT_SEEDS);
        RULES.put("biol", Items.WHEAT_SEEDS);
        RULES.put("foldrajz", Items.FILLED_MAP);
        RULES.put("foldm", Items.FILLED_MAP);

        // Computing.
        RULES.put("digitalis", Items.COMPARATOR);
        RULES.put("informatik", Items.COMPARATOR);
        RULES.put("programoz", Items.REPEATER);
        RULES.put("backend", Items.REPEATER);
        RULES.put("frontend", Items.REPEATER);
        RULES.put("adatbazis", Items.CHEST);
        RULES.put("halozat", Items.LEAD);
        RULES.put("hardver", Items.REDSTONE_TORCH);
        RULES.put("szoftver", Items.COMMAND_BLOCK);

        // Languages and humanities.
        RULES.put("angol", Items.BOOK);
        RULES.put("nemet", Items.BOOK);
        RULES.put("idegen nyelv", Items.BOOK);
        RULES.put("magyar", Items.WRITTEN_BOOK);
        RULES.put("irodalom", Items.WRITTEN_BOOK);
        RULES.put("nyelvtan", Items.WRITTEN_BOOK);
        RULES.put("tortenelem", Items.CLOCK);
        RULES.put("allampolgari", Items.PAPER);
        RULES.put("etika", Items.PAPER);
        RULES.put("filozof", Items.PAPER);

        // Maths.
        RULES.put("matematik", Items.PAPER);

        // Physical education and defence.
        RULES.put("testnevel", Items.LEATHER_BOOTS);
        RULES.put("testn", Items.LEATHER_BOOTS);
        RULES.put("honved", Items.IRON_SWORD);

        // Arts and crafts.
        RULES.put("enek", Items.NOTE_BLOCK);
        RULES.put("zene", Items.NOTE_BLOCK);
        RULES.put("rajz", Items.PAINTING);
        RULES.put("muveszet", Items.PAINTING);

        // Workshop and practicals.
        RULES.put("gyakorlat", Items.IRON_PICKAXE);
        RULES.put("muszaki", Items.IRON_PICKAXE);
        RULES.put("anyagism", Items.IRON_INGOT);
        RULES.put("gepesz", Items.PISTON);
        RULES.put("hidraul", Items.WATER_BUCKET);
        RULES.put("elektro", Items.LIGHTNING_ROD);

        // Exams and optional courses.
        RULES.put("fakultac", Items.ENCHANTED_BOOK);
        RULES.put("vizsga", Items.ENCHANTED_BOOK);
        RULES.put("verseny", Items.GOLD_INGOT);
    }

    private static final Item FALLBACK = Items.PAPER;

    private SubjectIcons() {
    }

    /**
     * The item for a subject, matched on its full name and then its short name.
     *
     * @param name      the long subject name, e.g. "Digitalis kultura"
     * @param shortName the abbreviation, e.g. "DiKu"
     */
    public static Item forSubject(String name, String shortName) {
        Item byName = match(name);
        if (byName != null) {
            return byName;
        }
        Item byShort = match(shortName);
        return byShort != null ? byShort : FALLBACK;
    }

    private static Item match(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalised = normalise(raw);
        for (Map.Entry<String, Item> rule : RULES.entrySet()) {
            if (normalised.contains(rule.getKey())) {
                return rule.getValue();
            }
        }
        return null;
    }

    /**
     * Lowercases and strips Hungarian accents so a single stem matches every
     * spelling the timetable uses ("Kemia", "kémia", "Alk.kém").
     */
    static String normalise(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            sb.append(fold(lower.charAt(i)));
        }
        return sb.toString();
    }

    private static char fold(char c) {
        return switch (c) {
            case 'á' -> 'a';
            case 'é' -> 'e';
            case 'í' -> 'i';
            case 'ó', 'ö', 'ő' -> 'o';
            case 'ú', 'ü', 'ű' -> 'u';
            default -> c;
        };
    }
}
