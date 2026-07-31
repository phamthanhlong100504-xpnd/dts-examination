import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckExamRules {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://103.75.182.249:5434/examination";
        String user = "postgres";
        String password = "K59Ptl*100504";
        
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            
            ResultSet rs = stmt.executeQuery(
                "SELECT column_name, data_type, is_nullable, column_default " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'exam_rules' " +
                "ORDER BY ordinal_position"
            );
            
            System.out.println("=== exam_rules table columns ===");
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.printf("%2d. %-40s %-20s nullable=%s default=%s%n",
                    count,
                    rs.getString("column_name"),
                    rs.getString("data_type"),
                    rs.getString("is_nullable"),
                    rs.getString("column_default"));
            }
            
            if (count == 0) {
                System.out.println("ERROR: Table exam_rules does not exist!");
            } else {
                System.out.println("\nTotal columns: " + count);
            }
        }
    }
}
