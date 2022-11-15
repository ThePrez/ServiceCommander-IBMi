package jesseg.ibmi.opensource.utils;

import java.util.EnumMap;

import com.github.theprez.jcmdutils.StringUtils.TerminalColor;

public class ColorSchemeConfig {
    public enum ColorScheme {
        RUNNING, NOT_RUNNING, INFO, WARNING, ERROR, PLAIN, STATUS;
    }

    public static EnumMap<ColorScheme, TerminalColor> m_colorConfig;

    static {
        m_colorConfig = new EnumMap<>(ColorScheme.class);
        m_colorConfig.put(ColorScheme.RUNNING, TerminalColor.GREEN);
        m_colorConfig.put(ColorScheme.NOT_RUNNING, TerminalColor.PURPLE);
        m_colorConfig.put(ColorScheme.INFO, TerminalColor.CYAN);
        m_colorConfig.put(ColorScheme.WARNING, TerminalColor.YELLOW);
        m_colorConfig.put(ColorScheme.ERROR, TerminalColor.BRIGHT_RED);
        m_colorConfig.put(ColorScheme.PLAIN, TerminalColor.WHITE);
        m_colorConfig.put(ColorScheme.STATUS, TerminalColor.BLUE);
    }

    public ColorSchemeConfig() {}

    public static void updateColor(String _context, String _color) {
        try {
            m_colorConfig.put(ColorScheme.valueOf(_context), TerminalColor.valueOf(_color));
        } catch (IllegalArgumentException e ) {
            System.out.println(String.format("ERROR: unable to override terminal color defaults. invalid color context or color: [%s:%s]", _context, _color));
        }
    }

    public static TerminalColor get(String _context) {
        return m_colorConfig.get(ColorScheme.valueOf(_context));
    }



    





    








    
}
