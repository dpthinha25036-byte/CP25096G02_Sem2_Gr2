package ctv.gui;

/** Lớp chạy cho jar đóng gói (shade): main không kế thừa Application nên JavaFX chạy được khi không dùng module path. */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) { MainApp.main(args); }
}
