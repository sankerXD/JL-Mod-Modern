package org.snapvm.adapter;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import org.snapvm.Vm;

/**
 * Direct-dispatch registrations for the lcdui calls games pound every frame.
 * Each entry skips reflective lookup + argument marshal in HostBridge.
 * Values arrive in guest representation: int-family as Integer (booleans as
 * 0/1), long as Long, refs as raw objects.
 */
final class LcduiFastCalls {
	private LcduiFastCalls() {
	}

	private static int i(Object o) {
		return (Integer) o;
	}

	static void register(Vm vm) {
		final String G = "javax/microedition/lcdui/Graphics.";
		vm.registerFastCall(G + "drawImage(Ljavax/microedition/lcdui/Image;III)V",
				a -> { ((Graphics) a[0]).drawImage((Image) a[1], i(a[2]), i(a[3]), i(a[4])); return null; });
		vm.registerFastCall(G + "drawRegion(Ljavax/microedition/lcdui/Image;IIIIIIII)V",
				a -> { ((Graphics) a[0]).drawRegion((Image) a[1], i(a[2]), i(a[3]), i(a[4]),
						i(a[5]), i(a[6]), i(a[7]), i(a[8]), i(a[9])); return null; });
		vm.registerFastCall(G + "drawRGB([IIIIIIIZ)V",
				a -> { ((Graphics) a[0]).drawRGB((int[]) a[1], i(a[2]), i(a[3]), i(a[4]),
						i(a[5]), i(a[6]), i(a[7]), i(a[8]) != 0); return null; });
		vm.registerFastCall(G + "drawLine(IIII)V",
				a -> { ((Graphics) a[0]).drawLine(i(a[1]), i(a[2]), i(a[3]), i(a[4])); return null; });
		vm.registerFastCall(G + "drawRect(IIII)V",
				a -> { ((Graphics) a[0]).drawRect(i(a[1]), i(a[2]), i(a[3]), i(a[4])); return null; });
		vm.registerFastCall(G + "fillRect(IIII)V",
				a -> { ((Graphics) a[0]).fillRect(i(a[1]), i(a[2]), i(a[3]), i(a[4])); return null; });
		vm.registerFastCall(G + "drawRoundRect(IIIIII)V",
				a -> { ((Graphics) a[0]).drawRoundRect(i(a[1]), i(a[2]), i(a[3]), i(a[4]),
						i(a[5]), i(a[6])); return null; });
		vm.registerFastCall(G + "fillRoundRect(IIIIII)V",
				a -> { ((Graphics) a[0]).fillRoundRect(i(a[1]), i(a[2]), i(a[3]), i(a[4]),
						i(a[5]), i(a[6])); return null; });
		vm.registerFastCall(G + "drawArc(IIIIII)V",
				a -> { ((Graphics) a[0]).drawArc(i(a[1]), i(a[2]), i(a[3]), i(a[4]),
						i(a[5]), i(a[6])); return null; });
		vm.registerFastCall(G + "fillArc(IIIIII)V",
				a -> { ((Graphics) a[0]).fillArc(i(a[1]), i(a[2]), i(a[3]), i(a[4]),
						i(a[5]), i(a[6])); return null; });
		vm.registerFastCall(G + "fillTriangle(IIIIII)V",
				a -> { ((Graphics) a[0]).fillTriangle(i(a[1]), i(a[2]), i(a[3]), i(a[4]),
						i(a[5]), i(a[6])); return null; });
		vm.registerFastCall(G + "drawString(Ljava/lang/String;III)V",
				a -> { ((Graphics) a[0]).drawString((String) a[1], i(a[2]), i(a[3]), i(a[4])); return null; });
		vm.registerFastCall(G + "drawSubstring(Ljava/lang/String;IIIII)V",
				a -> { ((Graphics) a[0]).drawSubstring((String) a[1], i(a[2]), i(a[3]),
						i(a[4]), i(a[5]), i(a[6])); return null; });
		vm.registerFastCall(G + "drawChar(CIII)V",
				a -> { ((Graphics) a[0]).drawChar((char) i(a[1]), i(a[2]), i(a[3]), i(a[4])); return null; });
		vm.registerFastCall(G + "drawChars([CIIIII)V",
				a -> { ((Graphics) a[0]).drawChars((char[]) a[1], i(a[2]), i(a[3]),
						i(a[4]), i(a[5]), i(a[6])); return null; });
		vm.registerFastCall(G + "setColor(I)V",
				a -> { ((Graphics) a[0]).setColor(i(a[1])); return null; });
		vm.registerFastCall(G + "setColor(III)V",
				a -> { ((Graphics) a[0]).setColor(i(a[1]), i(a[2]), i(a[3])); return null; });
		vm.registerFastCall(G + "getColor()I", a -> ((Graphics) a[0]).getColor());
		vm.registerFastCall(G + "getRedComponent()I", a -> ((Graphics) a[0]).getRedComponent());
		vm.registerFastCall(G + "getGreenComponent()I", a -> ((Graphics) a[0]).getGreenComponent());
		vm.registerFastCall(G + "getBlueComponent()I", a -> ((Graphics) a[0]).getBlueComponent());
		vm.registerFastCall(G + "getGrayScale()I", a -> ((Graphics) a[0]).getGrayScale());
		vm.registerFastCall(G + "setGrayScale(I)V",
				a -> { ((Graphics) a[0]).setGrayScale(i(a[1])); return null; });
		vm.registerFastCall(G + "setFont(Ljavax/microedition/lcdui/Font;)V",
				a -> { ((Graphics) a[0]).setFont((Font) a[1]); return null; });
		vm.registerFastCall(G + "getFont()Ljavax/microedition/lcdui/Font;",
				a -> ((Graphics) a[0]).getFont());
		vm.registerFastCall(G + "setClip(IIII)V",
				a -> { ((Graphics) a[0]).setClip(i(a[1]), i(a[2]), i(a[3]), i(a[4])); return null; });
		vm.registerFastCall(G + "clipRect(IIII)V",
				a -> { ((Graphics) a[0]).clipRect(i(a[1]), i(a[2]), i(a[3]), i(a[4])); return null; });
		vm.registerFastCall(G + "getClipX()I", a -> ((Graphics) a[0]).getClipX());
		vm.registerFastCall(G + "getClipY()I", a -> ((Graphics) a[0]).getClipY());
		vm.registerFastCall(G + "getClipWidth()I", a -> ((Graphics) a[0]).getClipWidth());
		vm.registerFastCall(G + "getClipHeight()I", a -> ((Graphics) a[0]).getClipHeight());
		vm.registerFastCall(G + "setStrokeStyle(I)V",
				a -> { ((Graphics) a[0]).setStrokeStyle(i(a[1])); return null; });
		vm.registerFastCall(G + "getStrokeStyle()I", a -> ((Graphics) a[0]).getStrokeStyle());
		vm.registerFastCall(G + "translate(II)V",
				a -> { ((Graphics) a[0]).translate(i(a[1]), i(a[2])); return null; });
		vm.registerFastCall(G + "getTranslateX()I", a -> ((Graphics) a[0]).getTranslateX());
		vm.registerFastCall(G + "getTranslateY()I", a -> ((Graphics) a[0]).getTranslateY());
		vm.registerFastCall(G + "copyArea(IIIIIII)V",
				a -> { ((Graphics) a[0]).copyArea(i(a[1]), i(a[2]), i(a[3]), i(a[4]),
						i(a[5]), i(a[6]), i(a[7])); return null; });

		final String I = "javax/microedition/lcdui/Image.";
		vm.registerFastCall(I + "getWidth()I", a -> ((Image) a[0]).getWidth());
		vm.registerFastCall(I + "getHeight()I", a -> ((Image) a[0]).getHeight());
		vm.registerFastCall(I + "getGraphics()Ljavax/microedition/lcdui/Graphics;",
				a -> ((Image) a[0]).getGraphics());
		vm.registerFastCall(I + "getRGB([IIIIIII)V",
				a -> { ((Image) a[0]).getRGB((int[]) a[1], i(a[2]), i(a[3]), i(a[4]),
						i(a[5]), i(a[6]), i(a[7])); return null; });
		vm.registerFastCall(I + "isMutable()Z", a -> ((Image) a[0]).isMutable() ? 1 : 0);

		final String F = "javax/microedition/lcdui/Font.";
		vm.registerFastCall(F + "getHeight()I", a -> ((Font) a[0]).getHeight());
		vm.registerFastCall(F + "getSize()I", a -> ((Font) a[0]).getSize());
		vm.registerFastCall(F + "getStyle()I", a -> ((Font) a[0]).getStyle());
		vm.registerFastCall(F + "getFace()I", a -> ((Font) a[0]).getFace());
		vm.registerFastCall(F + "getBaselinePosition()I", a -> ((Font) a[0]).getBaselinePosition());
		vm.registerFastCall(F + "stringWidth(Ljava/lang/String;)I",
				a -> ((Font) a[0]).stringWidth((String) a[1]));
		vm.registerFastCall(F + "charWidth(C)I", a -> ((Font) a[0]).charWidth((char) i(a[1])));
		vm.registerFastCall(F + "charsWidth([CII)I",
				a -> ((Font) a[0]).charsWidth((char[]) a[1], i(a[2]), i(a[3])));
		vm.registerFastCall(F + "substringWidth(Ljava/lang/String;II)I",
				a -> ((Font) a[0]).substringWidth((String) a[1], i(a[2]), i(a[3])));
		vm.registerFastCall(F + "getDefaultFont()Ljavax/microedition/lcdui/Font;",
				a -> Font.getDefaultFont());
		vm.registerFastCall(F + "getFont(III)Ljavax/microedition/lcdui/Font;",
				a -> Font.getFont(i(a[0]), i(a[1]), i(a[2])));
		vm.registerFastCall(F + "getFont(I)Ljavax/microedition/lcdui/Font;",
				a -> Font.getFont(i(a[0])));
	}
}
