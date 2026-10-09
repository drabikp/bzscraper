package sk.drabikp.bzscraper.shell;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * Around every page: the menu of the features' pages. A page is in it by its own {@code @Menu}
 * (title, order, icon), so no feature links to another feature's page and a new feature's page
 * shows up without changing this.
 */
@Layout
public class MainLayout extends AppLayout {

    public MainLayout() {
        Span name = new Span("Gig sync hub");
        name.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.Padding.MEDIUM);
        SideNav menu = new SideNav();
        MenuConfiguration.getMenuEntries().forEach(entry -> menu.addItem(item(entry)));
        addToDrawer(name, menu);
        addToNavbar(new DrawerToggle());
        setPrimarySection(Section.DRAWER);
    }

    private static SideNavItem item(MenuEntry entry) {
        return entry.icon() == null || entry.icon().isBlank() ? new SideNavItem(entry.title(), entry.path())
                : new SideNavItem(entry.title(), entry.path(), new Icon(entry.icon()));
    }
}
