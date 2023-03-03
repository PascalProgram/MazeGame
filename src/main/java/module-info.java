module de.rieger.pascal.mazegame {
    requires javafx.controls;
    requires javafx.fxml;


    opens de.rieger.pascal.mazegame to javafx.fxml;
    exports de.rieger.pascal.mazegame;
}