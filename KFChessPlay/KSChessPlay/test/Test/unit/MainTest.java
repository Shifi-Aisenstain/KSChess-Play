//package test.Test.unit;
//
//import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
//
//import java.io.ByteArrayInputStream;
//
//import org.junit.jupiter.api.Test;
//
//class MainTest {
//
//    @Test
//   void mainRunsSuccessfully() {
//        String input = """
//                Board:
//                .
//
//                """;
//
//        System.setIn(new ByteArrayInputStream(input.getBytes()));
//
//        assertDoesNotThrow(() -> Main.main(new String[]{}));
//    }
//}