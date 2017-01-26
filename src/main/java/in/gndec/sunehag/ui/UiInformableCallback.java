package in.gndec.sunehag.ui;

public interface UiInformableCallback<T> extends UiCallback<T> {
    void inform(String text);
}
