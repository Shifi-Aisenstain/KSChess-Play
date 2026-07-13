package input;

import models.Position;

public class BoardMapper {

    /**
     * מתרגם קורדינאטה טקסטואלית (למשל "a1") לאובייקט Position (שורה ועמודה)
     */
    public Position mapStringToPosition(String input) {
        if (input == null || input.length() < 2) {
            throw new IllegalArgumentException("קלט לא תקין");
        }

        char file = input.toLowerCase().charAt(0); // למשל 'a'
        char rank = input.charAt(1);               // למשל '1'

        int col = file - 'a';
        int row = Character.getNumericValue(rank) - 1;

        return new Position(row, col);
    }
}