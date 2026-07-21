package models;

import lombok.Value;

@Value
public class Piece {
    char color; // 'w' או 'b'
    char type;  // 'R', 'K', 'P', 'Q'
}
