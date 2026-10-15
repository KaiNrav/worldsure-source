package io.github.kainrav.worldsure;

import java.util.Comparator;

/**
 * Orders mod version strings well enough to spot a downgrade: numeric parts compare as
 * numbers, everything else as text, and a pre-release ({@code 1.2.0-beta}) sorts before
 * its release. Build metadata after {@code +} is ignored.
 */
public final class VersionComparator implements Comparator<String> {
    public static final VersionComparator INSTANCE = new VersionComparator();

    private VersionComparator() {
    }

    @Override
    public int compare(String a, String b) {
        String coreA = stripBuild(a);
        String coreB = stripBuild(b);
        String[] mainA = mainPart(coreA).split("[._]");
        String[] mainB = mainPart(coreB).split("[._]");
        int length = Math.max(mainA.length, mainB.length);
        for (int i = 0; i < length; i++) {
            String partA = i < mainA.length ? mainA[i] : "0";
            String partB = i < mainB.length ? mainB[i] : "0";
            int result = comparePart(partA, partB);
            if (result != 0) {
                return result;
            }
        }
        String preA = preRelease(coreA);
        String preB = preRelease(coreB);
        if (preA.isEmpty() || preB.isEmpty()) {
            // A release is newer than any pre-release of the same version.
            return Boolean.compare(preA.isEmpty(), preB.isEmpty());
        }
        return comparePart(preA, preB);
    }

    private static String stripBuild(String version) {
        int plus = version.indexOf('+');
        return plus < 0 ? version : version.substring(0, plus);
    }

    private static String mainPart(String version) {
        int dash = version.indexOf('-');
        return dash < 0 ? version : version.substring(0, dash);
    }

    private static String preRelease(String version) {
        int dash = version.indexOf('-');
        return dash < 0 ? "" : version.substring(dash + 1);
    }

    private static int comparePart(String a, String b) {
        boolean numericA = !a.isEmpty() && a.chars().allMatch(Character::isDigit);
        boolean numericB = !b.isEmpty() && b.chars().allMatch(Character::isDigit);
        if (numericA && numericB) {
            String trimmedA = a.replaceFirst("^0+(?=.)", "");
            String trimmedB = b.replaceFirst("^0+(?=.)", "");
            if (trimmedA.length() != trimmedB.length()) {
                return Integer.compare(trimmedA.length(), trimmedB.length());
            }
            return trimmedA.compareTo(trimmedB);
        }
        if (numericA != numericB) {
            // Numbers sort after words, so "1.0.rc1" < "1.0.1".
            return numericA ? 1 : -1;
        }
        return a.compareToIgnoreCase(b);
    }
}
