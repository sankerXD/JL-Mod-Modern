package org.snapvm.adapter;

import java.lang.reflect.Field;
import java.util.Vector;

import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.CompositingMode;
import javax.microedition.m3g.Fog;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.IndexBuffer;
import javax.microedition.m3g.Light;
import javax.microedition.m3g.Material;
import javax.microedition.m3g.Object3D;
import javax.microedition.m3g.PolygonMode;
import javax.microedition.m3g.Texture2D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.Transformable;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;

import org.snapvm.HostHandle;
import org.snapvm.Vm;
import org.snapvm.VmError;

/**
 * HostHandle codecs for javax.microedition.m3g objects kept in guest state
 * (3D足球 et al). The m3g implementation is native-peer backed — parameters
 * pushed via setters are not reachable through reflection, so codecs capture
 * through the public getters and rebuild through constructors + setters.
 *
 * State the API cannot read back at all (strip lengths, bound transforms,
 * Image2D source pixels) is mirrored in small Java-side cache fields added
 * to the m3g classes — see "Snapshot caches" there.
 *
 * Scene-graph types (World/Group/Node/Mesh/Sprite3D/Animation*) are not
 * handled: games that keep them in guest fields fail the save loudly with
 * the class name, which is the intended discovery signal.
 */
public final class SnapM3gHandles {
	private SnapM3gHandles() {
	}

	public static void registerAll(Vm vm) {
		vm.registerHostHandle(Graphics3D.class, new Graphics3DHandle());
		vm.registerHostHandle(Transform.class, new TransformHandle());
		vm.registerHostHandle(Camera.class, new CameraHandle());
		vm.registerHostHandle(Light.class, new LightHandle());
		vm.registerHostHandle(VertexBuffer.class, new VertexBufferHandle());
		vm.registerHostHandle(VertexArray.class, new VertexArrayHandle());
		vm.registerHostHandle(IndexBuffer.class, new IndexBufferHandle());
		vm.registerHostHandle(Appearance.class, new AppearanceHandle());
		vm.registerHostHandle(CompositingMode.class, new CompositingModeHandle());
		vm.registerHostHandle(PolygonMode.class, new PolygonModeHandle());
		vm.registerHostHandle(Material.class, new MaterialHandle());
		vm.registerHostHandle(Fog.class, new FogHandle());
		vm.registerHostHandle(Texture2D.class, new Texture2DHandle());
		vm.registerHostHandle(Image2D.class, new Image2DHandle());
		vm.registerHostHandle(Background.class, new BackgroundHandle());
	}

	// ----- shared tails ---------------------------------------------------

	/** Object3D tail: userID + userObject (may be a guest ref — fine). */
	private static Object[] captureObj3D(Object3D o) {
		if (o.getAnimationTrackCount() > 0) {
			throw new VmError("cannot snapshot " + o.getClass().getName()
					+ " — AnimationTrack not supported yet");
		}
		return new Object[]{o.getUserID(), o.getUserObject()};
	}

	private static void restoreObj3D(Object3D o, Object[] tail) {
		o.setUserID((Integer) tail[0]);
		o.setUserObject(tail[1]);
	}

	/** Transformable tail: the composite local transform. */
	private static Transform captureTransformable(Transformable t) {
		Transform x = new Transform();
		t.getTransform(x);
		return x;
	}

	private static void restoreTransformable(Transformable t, Object rec) {
		if (rec != null) {
			t.setTransform((Transform) rec);
		}
	}

	// ----- codecs ---------------------------------------------------------

	/**
	 * Singleton: revive reuses the live instance — clear its bind state and
	 * re-apply target/viewport/camera/lights from the caches the class keeps.
	 */
	private static final class Graphics3DHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			try {
				Class<?> c = Graphics3D.class;
				Vector<?> lights = (Vector<?>) field(c, "lights").get(h);
				Vector<?> lts = (Vector<?>) field(c, "lightTransforms").get(h);
				return new Object[]{
						field(c, "currentTarget").get(h),
						field(c, "hints").get(h),
						field(c, "depthEnabled").get(h),
						field(c, "vpX").get(h), field(c, "vpY").get(h),
						field(c, "vpW").get(h), field(c, "vpH").get(h),
						field(c, "camera").get(h),
						field(c, "cameraTransform").get(h),
						lights.toArray(), lts.toArray(),
				};
			} catch (Exception e) {
				throw new VmError("Graphics3D capture failed", e);
			}
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			try {
				Graphics3D g = Graphics3D.getInstance();
				g.resetLights();
				Object bound = field(Graphics3D.class, "currentTarget").get(g);
				if (bound != null) {
					g.releaseTarget();
				}
				Object target = r[0];
				if (target != null) {
					g.bindTarget(target, (Boolean) r[2], (Integer) r[1]);
				}
				if ((Integer) r[5] > 0) {
					g.setViewport((Integer) r[3], (Integer) r[4],
							(Integer) r[5], (Integer) r[6]);
				}
				if (r[7] != null) {
					g.setCamera((Camera) r[7], (Transform) r[8]);
				}
				Object[] lights = (Object[]) r[9];
				Object[] lts = (Object[]) r[10];
				for (int i = 0; i < lights.length; i++) {
					if (lights[i] != null) {
						g.addLight((Light) lights[i],
								i < lts.length ? (Transform) lts[i] : null);
					}
				}
				return g;
			} catch (Exception e) {
				throw new VmError("Graphics3D revive failed", e);
			}
		}
	}

	/** byte[72] matrix, package-private field. */
	private static final class TransformHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			try {
				return ((byte[]) field(Transform.class, "matrix").get(h)).clone();
			} catch (Exception e) {
				throw new VmError("Transform capture failed", e);
			}
		}

		public Object revive(Vm vm, Object rec) {
			try {
				Transform t = new Transform();
				field(Transform.class, "matrix").set(t, ((byte[]) rec).clone());
				return t;
			} catch (Exception e) {
				throw new VmError("Transform revive failed", e);
			}
		}
	}

	private static final class CameraHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			Camera c = (Camera) h;
			float[] params = new float[4];
			int type = c.getProjection(params);
			Transform projT = null;
			if (type == Camera.GENERIC) {
				projT = new Transform();
				c.getProjection(projT);
			}
			return new Object[]{type, params, projT,
					captureTransformable(c), captureObj3D(c)};
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			Camera c = new Camera();
			float[] p = (float[]) r[1];
			switch ((Integer) r[0]) {
			case Camera.PARALLEL:
				c.setParallel(p[0], p[1], p[2], p[3]);
				break;
			case Camera.PERSPECTIVE:
				c.setPerspective(p[0], p[1], p[2], p[3]);
				break;
			case Camera.GENERIC:
				c.setGeneric((Transform) r[2]);
				break;
			}
			restoreTransformable(c, r[3]);
			restoreObj3D(c, (Object[]) r[4]);
			return c;
		}
	}

	private static final class LightHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			Light l = (Light) h;
			return new Object[]{l.getMode(), l.getColor(), l.getIntensity(),
					l.getConstantAttenuation(), l.getLinearAttenuation(),
					l.getQuadraticAttenuation(),
					captureTransformable(l), captureObj3D(l)};
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			Light l = new Light();
			l.setMode((Integer) r[0]);
			l.setColor((Integer) r[1]);
			l.setIntensity((Float) r[2]);
			l.setAttenuation((Float) r[3], (Float) r[4], (Float) r[5]);
			restoreTransformable(l, r[6]);
			restoreObj3D(l, (Object[]) r[7]);
			return l;
		}
	}

	private static final class VertexArrayHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			VertexArray a = (VertexArray) h;
			int n = a.getVertexCount(), comps = a.getComponentCount();
			int size = a.getComponentType();
			Object data;
			if (size == 2) {
				short[] s = new short[n * comps];
				a.get(0, n, s);
				data = s;
			} else {
				byte[] b = new byte[n * comps];
				a.get(0, n, b);
				data = b;
			}
			return new Object[]{n, comps, size, data};
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			VertexArray a = new VertexArray((Integer) r[0], (Integer) r[1],
					(Integer) r[2]);
			if (r[3] instanceof short[]) {
				a.set(0, (Integer) r[0], (short[]) r[3]);
			} else {
				a.set(0, (Integer) r[0], (byte[]) r[3]);
			}
			return a;
		}
	}

	private static final class VertexBufferHandle implements HostHandle {
		private static Object[] slot(VertexArray a, float[] scaleBias) {
			return new Object[]{a, scaleBias};
		}

		public Object capture(Vm vm, Object h) {
			try {
				VertexBuffer vb = (VertexBuffer) h;
				float[] sb = new float[4];
				Object pos = null;
				VertexArray pa = vb.getPositions(sb);
				if (pa != null) {
					pos = slot(pa, sb.clone());
				}
				Object nor = vb.getNormals();
				Object col = vb.getColors();
				VertexArray[] units = (VertexArray[])
						field(VertexBuffer.class, "texCoords").get(h);
				Object[][] tex = new Object[units == null ? 0 : units.length][];
				for (int i = 0; i < tex.length; i++) {
					VertexArray ta = vb.getTexCoords(i, sb);
					if (ta != null) {
						tex[i] = slot(ta, sb.clone());
					}
				}
				return new Object[]{pos, nor, col, tex, vb.getDefaultColor(),
						captureObj3D(vb)};
			} catch (Exception e) {
				throw new VmError("VertexBuffer capture failed", e);
			}
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			VertexBuffer vb = new VertexBuffer();
			if (r[0] != null) {
				Object[] s = (Object[]) r[0];
				float[] sb = (float[]) s[1];
				vb.setPositions((VertexArray) s[0], sb[0],
						new float[]{sb[1], sb[2], sb[3]});
			}
			if (r[1] != null) {
				vb.setNormals((VertexArray) r[1]);
			}
			if (r[2] != null) {
				vb.setColors((VertexArray) r[2]);
			}
			Object[][] tex = (Object[][]) r[3];
			for (int i = 0; i < tex.length; i++) {
				if (tex[i] != null) {
					float[] sb = (float[]) tex[i][1];
					vb.setTexCoords(i, (VertexArray) tex[i][0], sb[0],
							new float[]{sb[1], sb[2], sb[3]});
				}
			}
			vb.setDefaultColor((Integer) r[4]);
			restoreObj3D(vb, (Object[]) r[5]);
			return vb;
		}
	}

	/**
	 * TriangleStripArray: implicit revives via firstIndex+stripLengths;
	 * explicit must replay the ORIGINAL index stream — getIndices() returns
	 * the expanded triangle list, which the ctor would re-join as strips.
	 */
	private static final class IndexBufferHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			IndexBuffer ib = (IndexBuffer) h;
			Object indices = null;
			Object strips = null;
			Object firstIndex = 0;
			Object implicit = Boolean.FALSE;
			try {
				Class<?> c = ib.getClass();
				strips = field(c, "stripLengths").get(ib);
				firstIndex = field(c, "firstIndex").get(ib);
				implicit = field(c, "implicit").get(ib);
				if (Boolean.FALSE.equals(implicit)) {
					indices = field(c, "indices").get(ib);
				}
			} catch (NoSuchFieldException | IllegalAccessException e) {
				throw new VmError(ib.getClass().getName()
						+ " has no cached fields — cannot snapshot");
			}
			if (indices == null && Boolean.FALSE.equals(implicit)) {
				int[] raw = new int[ib.getIndexCount()];
				ib.getIndices(raw);
				indices = raw;
			}
			return new Object[]{ib.getClass().getName(), indices, strips,
					firstIndex, implicit, captureObj3D(ib)};
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			IndexBuffer ib;
			if (TriangleStripArray.class.getName().equals(r[0])) {
				if (Boolean.TRUE.equals(r[4])) {
					ib = new TriangleStripArray((Integer) r[3],
							(int[]) r[2]);
				} else {
					int[] idx = (int[]) r[1];
					int[] strips = (int[]) r[2];
					int rawLen = 0;
					for (int l : strips) {
						rawLen += l;
					}
					if (idx.length != rawLen) {
						idx = unexpand(idx, strips);
					}
					ib = new TriangleStripArray(idx, strips);
				}
			} else {
				throw new VmError("cannot revive IndexBuffer " + r[0]);
			}
			restoreObj3D(ib, (Object[]) r[5]);
			return ib;
		}

		/**
		 * Old snapshots stored getIndices() — the expanded triangle list —
		 * as if it were the explicit strip stream. Rebuild each strip's
		 * vertex sequence: expanded tri j of strip i emits joined[s+j]
		 * (always v_j) then v_{j+1},v_{j+2} in order when s+j is even,
		 * swapped when odd; s advances by L + (L odd ? 3 : 2) per strip.
		 */
		private static int[] unexpand(int[] e, int[] strips) {
			int total = 0;
			for (int l : strips) {
				total += l;
			}
			int[] v = new int[total];
			int o = 0, s = 0, b = 0;
			for (int len : strips) {
				if (len >= 3) {
					v[o] = e[b];
					if ((s & 1) == 0) {
						v[o + 1] = e[b + 1];
						v[o + 2] = e[b + 2];
					} else {
						v[o + 1] = e[b + 2];
						v[o + 2] = e[b + 1];
					}
					for (int j = 1; j < len - 2; j++) {
						v[o + j + 2] = ((s + j) & 1) == 0
								? e[b + 3 * j + 2] : e[b + 3 * j + 1];
					}
				}
				o += len;
				s += len + ((len & 1) != 0 ? 3 : 2);
				b += 3 * (len - 2);
			}
			return v;
		}
	}

	private static final class AppearanceHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			try {
				Appearance a = (Appearance) h;
				Texture2D[] tex = (Texture2D[])
						field(Appearance.class, "textures").get(h);
				return new Object[]{a.getCompositingMode(), a.getFog(),
						a.getMaterial(), a.getPolygonMode(),
						tex != null ? tex : new Texture2D[0], a.getLayer(),
						captureObj3D(a)};
			} catch (Exception e) {
				throw new VmError("Appearance capture failed", e);
			}
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			Appearance a = new Appearance();
			a.setCompositingMode((CompositingMode) r[0]);
			a.setFog((Fog) r[1]);
			a.setMaterial((Material) r[2]);
			a.setPolygonMode((PolygonMode) r[3]);
			Object[] tex = (Object[]) r[4];
			for (int i = 0; i < tex.length; i++) {
				if (tex[i] != null) {
					a.setTexture(i, (Texture2D) tex[i]);
				}
			}
			a.setLayer((Integer) r[5]);
			restoreObj3D(a, (Object[]) r[6]);
			return a;
		}
	}

	private static final class CompositingModeHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			CompositingMode c = (CompositingMode) h;
			return new Object[]{c.getBlending(), c.getAlphaThreshold(),
					c.isAlphaWriteEnabled(), c.isColorWriteEnabled(),
					c.isDepthWriteEnabled(), c.isDepthTestEnabled(),
					c.getDepthOffsetFactor(), c.getDepthOffsetUnits(),
					captureObj3D(c)};
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			CompositingMode c = new CompositingMode();
			c.setBlending((Integer) r[0]);
			c.setAlphaThreshold((Float) r[1]);
			c.setAlphaWriteEnable((Boolean) r[2]);
			c.setColorWriteEnable((Boolean) r[3]);
			c.setDepthWriteEnable((Boolean) r[4]);
			c.setDepthTestEnable((Boolean) r[5]);
			c.setDepthOffset((Float) r[6], (Float) r[7]);
			restoreObj3D(c, (Object[]) r[8]);
			return c;
		}
	}

	private static final class PolygonModeHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			PolygonMode p = (PolygonMode) h;
			return new Object[]{p.getCulling(), p.getWinding(), p.getShading(),
					p.isTwoSidedLightingEnabled(),
					p.isLocalCameraLightingEnabled(),
					p.isPerspectiveCorrectionEnabled(), captureObj3D(p)};
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			PolygonMode p = new PolygonMode();
			p.setCulling((Integer) r[0]);
			p.setWinding((Integer) r[1]);
			p.setShading((Integer) r[2]);
			p.setTwoSidedLightingEnable((Boolean) r[3]);
			p.setLocalCameraLightingEnable((Boolean) r[4]);
			p.setPerspectiveCorrectionEnable((Boolean) r[5]);
			restoreObj3D(p, (Object[]) r[6]);
			return p;
		}
	}

	private static final class MaterialHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			Material m = (Material) h;
			return new Object[]{m.getColor(Material.AMBIENT),
					m.getColor(Material.DIFFUSE), m.getColor(Material.EMISSIVE),
					m.getColor(Material.SPECULAR), m.getShininess(),
					m.isVertexColorTrackingEnabled(), captureObj3D(m)};
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			Material m = new Material();
			m.setColor(Material.AMBIENT, (Integer) r[0]);
			m.setColor(Material.DIFFUSE, (Integer) r[1]);
			m.setColor(Material.EMISSIVE, (Integer) r[2]);
			m.setColor(Material.SPECULAR, (Integer) r[3]);
			m.setShininess((Float) r[4]);
			m.setVertexColorTrackingEnable((Boolean) r[5]);
			restoreObj3D(m, (Object[]) r[6]);
			return m;
		}
	}

	private static final class FogHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			Fog f = (Fog) h;
			return new Object[]{f.getMode(), f.getNearDistance(),
					f.getFarDistance(), f.getDensity(), f.getColor(),
					captureObj3D(f)};
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			Fog f = new Fog();
			f.setMode((Integer) r[0]);
			f.setLinear((Float) r[1], (Float) r[2]);
			f.setDensity((Float) r[3]);
			f.setColor((Integer) r[4]);
			restoreObj3D(f, (Object[]) r[5]);
			return f;
		}
	}

	private static final class Texture2DHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			Texture2D t = (Texture2D) h;
			return new Object[]{t.getImage(), t.getLevelFilter(),
					t.getImageFilter(), t.getWrappingS(), t.getWrappingT(),
					t.getBlending(), t.getBlendColor(),
					captureTransformable(t), captureObj3D(t)};
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			if (r[0] == null) {
				throw new VmError("Texture2D without image — cannot revive");
			}
			Texture2D t = new Texture2D((Image2D) r[0]);
			t.setFiltering((Integer) r[1], (Integer) r[2]);
			t.setWrapping((Integer) r[3], (Integer) r[4]);
			t.setBlending((Integer) r[5]);
			t.setBlendColor((Integer) r[6]);
			restoreTransformable(t, r[7]);
			restoreObj3D(t, (Object[]) r[8]);
			return t;
		}
	}

	/** Source pixels/lcd image come from the caches the class keeps. */
	private static final class Image2DHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			try {
				Image2D i = (Image2D) h;
				Class<?> c = Image2D.class;
				Object src = field(c, "srcImage").get(h);
				byte[] px = (byte[]) field(c, "srcPixels").get(h);
				Object pal = field(c, "srcPalette").get(h);
				return new Object[]{i.getFormat(), i.getWidth(), i.getHeight(),
						i.isMutable(), src, px, pal};
			} catch (Exception e) {
				throw new VmError("Image2D capture failed", e);
			}
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			int fmt = (Integer) r[0], w = (Integer) r[1], ht = (Integer) r[2];
			boolean mutable = (Boolean) r[3];
			Image2D i;
			if (r[4] != null) {
				i = new Image2D(fmt, r[4]);
			} else if (mutable) {
				i = new Image2D(fmt, w, ht);
				if (r[5] != null) {
					i.set(0, 0, w, ht, (byte[]) r[5]);
				}
			} else if (r[6] != null) {
				i = new Image2D(fmt, w, ht, (byte[]) r[5], (byte[]) r[6]);
			} else {
				i = new Image2D(fmt, w, ht, (byte[]) r[5]);
			}
			return i;
		}
	}

	private static final class BackgroundHandle implements HostHandle {
		public Object capture(Vm vm, Object h) {
			Background b = (Background) h;
			return new Object[]{b.getColor(), b.getImage(),
					b.getImageModeX(), b.getImageModeY(),
					b.isColorClearEnabled(), b.isDepthClearEnabled(),
					b.getCropX(), b.getCropY(), b.getCropWidth(),
					b.getCropHeight(), captureObj3D(b)};
		}

		public Object revive(Vm vm, Object rec) {
			Object[] r = (Object[]) rec;
			Background b = new Background();
			b.setColor((Integer) r[0]);
			b.setImage((Image2D) r[1]);
			b.setImageMode((Integer) r[2], (Integer) r[3]);
			b.setColorClearEnable((Boolean) r[4]);
			b.setDepthClearEnable((Boolean) r[5]);
			b.setCrop((Integer) r[6], (Integer) r[7],
					(Integer) r[8], (Integer) r[9]);
			restoreObj3D(b, (Object[]) r[10]);
			return b;
		}
	}

	private static Field field(Class<?> c, String name)
			throws NoSuchFieldException {
		for (Class<?> x = c; x != null; x = x.getSuperclass()) {
			try {
				Field f = x.getDeclaredField(name);
				f.setAccessible(true);
				return f;
			} catch (NoSuchFieldException ignored) {
			}
		}
		throw new NoSuchFieldException(name);
	}
}
