package com.realism;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Draws circular status meters (orbs) for the Realism plugin.  Each orb
 * represents the current value of a meter (hunger, thirst, durability) as
 * a partially filled pie chart and displays an icon in the centre.  Orbs
 * can be moved around the screen by holding ALT and dragging, and they
 * respect configuration settings for visibility and colours.  The overlay
 * draws up to three orbs horizontally with a small gap between them.
 */
@Singleton
public class RealismOrbOverlay extends Overlay
{
    /**
     * Enumeration of the different meter types this overlay can draw.  The
     * order here determines the order in which the orbs are rendered.
     */
    public enum MeterType
    {
        HUNGER,
        THIRST,
        DURABILITY
    }

    // Injected dependencies
    private final RealismPlugin plugin;
    private final RealismConfig config;
    private final Client client;

    // Orb dimensions and spacing
    private static final int ORB_SIZE = 36;
    private static final int ICON_SIZE = 18;
    private static final int GAP = 8;

    /**
     * Construct the overlay.  Sets up positioning and flags to allow the
     * orbs to be dragged.  The overlay is drawn above the scene so that it
     * is not clipped by other widgets.
     */
    @Inject
    public RealismOrbOverlay(final RealismPlugin plugin, final RealismConfig config, final Client client)
    {
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.HIGH);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setMovable(true);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        // Determine which meters to draw based on config
        MeterType[] meters = {
            MeterType.HUNGER,
            MeterType.THIRST,
            MeterType.DURABILITY
        };
        int x = 0;
        int y = 0;
        int drawn = 0;

        // Enable anti-aliasing for smoother circles and text
        Object oldAntialias = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (MeterType type : meters)
        {
            if (!shouldDraw(type))
            {
                continue;
            }
            double value = getValueForMeter(type);
            Color colour = plugin.getColourForMeter(type);
            BufferedImage icon = plugin.getIconForMeter(type);
            drawOrb(graphics, x, y, value, colour, icon, type);
            x += ORB_SIZE + GAP;
            drawn++;
        }

        // Restore rendering hint
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialias);

        // Return the size so RuneLite can allocate interaction bounds
        int width = drawn * ORB_SIZE + Math.max(0, drawn - 1) * GAP;
        return new Dimension(width, ORB_SIZE);
    }

    /**
     * Determine whether the given meter type should be drawn based on config.
     */
    private boolean shouldDraw(MeterType type)
    {
        switch (type)
        {
            case HUNGER:
                return config.showHunger();
            case THIRST:
                return config.showThirst();
            case DURABILITY:
                return config.showDurability();
            default:
                return false;
        }
    }

    /**
     * Fetch the current meter value (0–100) for the specified type.
     */
    private double getValueForMeter(MeterType type)
    {
        switch (type)
        {
            case HUNGER:
                return plugin.getHunger();
            case THIRST:
                return plugin.getThirst();
            case DURABILITY:
                return plugin.getAverageDurability();
            default:
                return 100.0;
        }
    }

    /**
     * Draw a single orb at the specified screen coordinates.  The orb
     * consists of a circular background, a coloured fill arc showing the
     * remaining percentage, the icon centred in the orb and optionally
     * percentage text beneath the orb.
     *
     * @param g      the graphics context
     * @param x      x coordinate relative to the overlay
     * @param y      y coordinate relative to the overlay
     * @param value  meter value (0–100)
     * @param colour colour used for the fill
     * @param icon   sprite to draw in the centre (may be null)
     * @param type   the meter type being drawn
     */
    private void drawOrb(Graphics2D g, int x, int y, double value, Color colour, BufferedImage icon, MeterType type)
    {
        // Ensure value bounds
        double frac = Math.max(0.0, Math.min(1.0, value / 100.0));

        // Derive colours: background is semi-transparent dark grey; fill is the user colour with higher alpha
        Color bg = new Color(0, 0, 0, 100);
        Color fill = new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 180);

        // Draw background circle
        g.setColor(bg);
        g.fillOval(x, y, ORB_SIZE, ORB_SIZE);

        // Draw fill arc (pie). Start angle 90° (top) and sweep negative to fill clockwise
        g.setColor(fill);
        int startAngle = 90;
        int arcAngle = (int) -(360 * frac);
        g.fillArc(x, y, ORB_SIZE, ORB_SIZE, startAngle, arcAngle);

        // Draw border circle
        g.setColor(Color.BLACK);
        g.drawOval(x, y, ORB_SIZE, ORB_SIZE);

        // Draw icon at centre
        if (icon != null)
        {
            int ix = x + (ORB_SIZE - ICON_SIZE) / 2;
            int iy = y + (ORB_SIZE - ICON_SIZE) / 2;
            g.drawImage(icon, ix, iy, ICON_SIZE, ICON_SIZE, null);
        }

        // Draw percentage text below the orb (if configured).  For simplicity
        // always draw the percent below; advanced positioning could be added.
        String text = String.format("%d%%", (int) Math.round(value));
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(10f));
        int textWidth = g.getFontMetrics().stringWidth(text);
        int tx = x + (ORB_SIZE - textWidth) / 2;
        int ty = y + ORB_SIZE + 12; // 12px below orb
        g.drawString(text, tx, ty);
    }
}