#include <gtk/gtk.h>
#include <stdio.h>

static void apply_menu_fix(void) {
    const gchar *prg = g_get_prgname();
    if (prg && g_str_has_prefix(prg, "wrapper-2.0")) {
        GtkCssProvider *provider = gtk_css_provider_new();
        gtk_css_provider_load_from_data(provider,
            "menu { background-image: none; padding: 2px; }\n",
            -1, NULL);
        gtk_style_context_add_provider_for_screen(
            gdk_screen_get_default(),
            GTK_STYLE_PROVIDER(provider),
            GTK_STYLE_PROVIDER_PRIORITY_USER + 100);
    }
}

void gtk_module_init(gint *argc, gchar ***argv) {
    apply_menu_fix();
}

void gtk_module_display_init(GdkDisplay *display) {
    apply_menu_fix();
}
