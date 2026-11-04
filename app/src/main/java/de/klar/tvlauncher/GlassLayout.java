package de.klar.tvlauncher;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/**
 * Nachbildung von Apples Liquid Glass für die Bedienebene (Leisten, Menüs) auf Android 5+.
 *
 * Echtes Liquid Glass bricht das Licht in Echtzeit; das kann Android vor Version 12 nicht.
 * Diese View zeichnet deshalb eine einmal weichgezeichnete Vorlage dessen, was hinter ihr
 * liegt, leicht vergrößert (Linse), dazu eine Abdunkelung für Lesbarkeit, ein Glanzlicht
 * oben, eine helle Innenkante und einen dunklen Außenrand.
 *
 * Regeln (HIG): nur für schwebende Bedienelemente, nie auf Kacheln oder Hintergrund,
 * nie Glas auf Glas – Inhalte darauf nutzen Füllungen.
 */
public class GlassLayout extends FrameLayout {

    private static final float LENS_ZOOM = 1.08f;

    private final Paint backdropPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final int[] loc = new int[2];

    private float radius = -1f; // < 0: Kapsel
    private float dim = 0.30f;
    private Bitmap backdrop;
    private float backdropScale = 1f;
    private float density;

    public GlassLayout(Context context) { this(context, null); }

    public GlassLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GlassLayout);
            radius = a.getDimension(R.styleable.GlassLayout_glassRadius, -1f);
            dim = a.getFloat(R.styleable.GlassLayout_glassDim, dim);
            a.recycle();
        }
        setWillNotDraw(false);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.2f * density);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(2f * density);
        edge.setColor(0x4D000000);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius());
            }
        });
        setClipToOutline(false);
    }

    /**
     * @param blurred weichgezeichnete Vorlage des ganzen Bildschirms
     * @param scale   Faktor von Vorlagen- zu Bildschirmpixeln
     */
    public void setBackdrop(Bitmap blurred, float scale) {
        backdrop = blurred;
        backdropScale = scale;
        backdropPaint.setShader(blurred == null ? null
                : new BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        invalidate();
    }

    private float cornerRadius() {
        return radius < 0 ? getHeight() / 2f : radius;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        sheen.setShader(new LinearGradient(0, 0, 0, h * 0.55f, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x99FFFFFF, 0x1FFFFFFF, 0x33FFFFFF},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        invalidateOutline();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = cornerRadius();
        rect.set(0, 0, getWidth(), getHeight());

        if (backdrop != null) {
            // Vorlage auf Bildschirmposition legen und um die Mitte vergrößern (Linseneffekt)
            getLocationOnScreen(loc);
            matrix.setScale(backdropScale, backdropScale);
            matrix.postTranslate(-loc[0], -loc[1]);
            matrix.postScale(LENS_ZOOM, LENS_ZOOM, getWidth() / 2f, getHeight() / 2f);
            backdropPaint.getShader().setLocalMatrix(matrix);
            canvas.drawRoundRect(rect, r, r, backdropPaint);
            fill.setColor((Math.round(dim * 255) << 24) | 0x0B1017);
            canvas.drawRoundRect(rect, r, r, fill);
            fill.setColor(0x14FFFFFF);
            canvas.drawRoundRect(rect, r, r, fill);
        } else {
            fill.setColor(0xE61C2430);
            canvas.drawRoundRect(rect, r, r, fill);
        }
        canvas.drawRoundRect(rect, r, r, sheen);

        float e = edge.getStrokeWidth() / 2f;
        rect.inset(e, e);
        canvas.drawRoundRect(rect, r - e, r - e, edge);
        float i = rim.getStrokeWidth() / 2f + e;
        rect.set(i, i, getWidth() - i, getHeight() - i);
        canvas.drawRoundRect(rect, r - i, r - i, rim);
    }
}
