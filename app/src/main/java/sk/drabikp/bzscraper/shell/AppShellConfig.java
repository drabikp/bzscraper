package sk.drabikp.bzscraper.shell;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.page.Viewport;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

@Theme("bzscraper")
@StyleSheet(Lumo.UTILITY_STYLESHEET)
@Viewport("width=device-width, initial-scale=1")
@Push // server push so background publish threads can update the UI via UI.access
public class AppShellConfig implements AppShellConfigurator {
}
