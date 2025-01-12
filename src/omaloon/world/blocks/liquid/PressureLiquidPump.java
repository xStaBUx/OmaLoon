package omaloon.world.blocks.liquid;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import omaloon.content.*;
import omaloon.utils.*;
import omaloon.world.interfaces.*;
import omaloon.world.meta.*;
import omaloon.world.modules.*;

import static mindustry.Vars.*;
import static mindustry.type.Liquid.*;

public class PressureLiquidPump extends Block {
	public PressureConfig pressureConfig = new PressureConfig();

	public float pumpStrength = 0.1f;

	public float pressureDifference = 10;

	public float liquidPadding = 3f;

	public TextureRegion[][] liquidRegions;
	public TextureRegion[] tiles;
	public TextureRegion arrowRegion, topRegion, bottomRegion;

	public PressureLiquidPump(String name) {
		super(name);
		rotate = true;
		destructible = true;
		update = true;
		saveConfig = copyConfig = true;
		config(Liquid.class, (PressureLiquidPumpBuild build, Liquid liquid) -> {
			build.filter = liquid.id;
		});
	}

	@Override
	public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list) {
		var tiling = new Object() {
			int tiling = 0;
		};
		Point2
			front = new Point2(1, 0).rotate(plan.rotation).add(plan.x, plan.y),
			back = new Point2(-1, 0).rotate(plan.rotation).add(plan.x, plan.y);

		boolean inverted = plan.rotation == 1 || plan.rotation == 2;
		list.each(next -> {
			if (!(next.block instanceof PressureLiquidPump)) {
				if (new Point2(next.x, next.y).equals(front) && next.block.outputsLiquid) tiling.tiling |= inverted ? 2 : 1;
				if (new Point2(next.x, next.y).equals(back) && next.block.outputsLiquid) tiling.tiling |= inverted ? 1 : 2;
			}
		});

		Draw.rect(bottomRegion, plan.drawx(), plan.drawy(), 0);
		if (tiling.tiling != 0) Draw.rect(arrowRegion, plan.drawx(), plan.drawy(), (plan.rotation) * 90f);
		Draw.rect(tiles[tiling.tiling], plan.drawx(), plan.drawy(), (plan.rotation + 1) * 90f % 180 - 90);
		if (tiling.tiling == 0) Draw.rect(topRegion, plan.drawx(), plan.drawy(), (plan.rotation) * 90f);
	}

	@Override public TextureRegion[] icons() {
		return new TextureRegion[]{region, topRegion};
	}

	@Override
	public void init() {
		super.init();

		pressureConfig.fluidGroup = FluidGroup.pumps;
	}

	@Override
	public void load() {
		super.load();
		tiles = OlUtils.split(name + "-tiles", 32, 0);
		arrowRegion = Core.atlas.find(name + "-arrow");
		topRegion = Core.atlas.find(name + "-top");
		bottomRegion = Core.atlas.find(name + "-bottom", "omaloon-liquid-bottom");

		liquidRegions = new TextureRegion[2][animationFrames];
		if(renderer != null){
			var frames = renderer.getFluidFrames();

			for (int fluid = 0; fluid < 2; fluid++) {
				for (int frame = 0; frame < animationFrames; frame++) {
					TextureRegion base = frames[fluid][frame];
					TextureRegion result = new TextureRegion();
					result.set(base);

					result.setHeight(result.height - liquidPadding);
					result.setWidth(result.width - liquidPadding);
					result.setX(result.getX() + liquidPadding);
					result.setY(result.getY() + liquidPadding);

					liquidRegions[fluid][frame] = result;
				}
			}
		}
	}

	@Override
	public void setStats() {
		super.setStats();
		pressureConfig.addStats(stats);
		stats.add(OlStats.pressureFlow, Mathf.round(pumpStrength * 60f, 2), OlStats.pressureSecond);
	}

	public class PressureLiquidPumpBuild extends Building implements HasPressure {
		PressureModule pressure = new PressureModule();

		public int tiling;
		public float smoothAlpha;

		public int filter = -1;

		@Override public boolean acceptsPressurizedFluid(HasPressure from, @Nullable Liquid liquid, float amount) {
			return false;
		}

		@Override
		public void buildConfiguration(Table table) {
			ItemSelection.buildTable(table, Vars.content.liquids(), () -> Vars.content.liquid(filter), other -> filter = other == null ? -1 : other.id);
		}

		/**
		 * Returns the length of the pump chain
		 */
		public int chainSize() {
			return pressure.section.builds.size;
		}

		@Override public boolean connects(HasPressure to) {
			return HasPressure.super.connects(to) && (front() == to || back() == to);
		}

		@Override
		public void draw() {
			float rot = rotate ? (90 + rotdeg()) % 180 - 90 : 0;
			if (tiling != 0) {
				Draw.rect(bottomRegion, x, y, rotdeg());

				HasPressure front = getTo();
				HasPressure back = getFrom();

				if (
					(front != null && front.pressure().getMain() != null) ||
					(back != null && back.pressure().getMain() != null)
				) {

					Color tmpColor = Tmp.c1;
					if (front != null && front.pressure().getMain() != null) {
						tmpColor.set(front.pressure().getMain().color);
					} else if (back != null && back.pressure().getMain() != null) {
						tmpColor.set(back.pressure().getMain().color);
					}

					if (
						front != null && front.pressure().getMain() != null &&
						back != null && back.pressure().getMain() != null
					) tmpColor.lerp(back.pressure().getMain().color, 0.5f);


					float alpha =
						(front != null && front.pressure().getMain() != null ? Mathf.clamp(front.pressure().liquids[front.pressure().getMain().id]/(front.pressure().liquids[front.pressure().getMain().id] + front.pressure().air)) : 0) +
						(back != null && back.pressure().getMain() != null ? Mathf.clamp(back.pressure().liquids[back.pressure().getMain().id]/(back.pressure().liquids[back.pressure().getMain().id] + back.pressure().air)) : 0);
					alpha /= ((front == null ? 0 : 1f) + (back == null ? 0 : 1f));

					smoothAlpha = Mathf.approachDelta(smoothAlpha, alpha, PressureModule.smoothingSpeed);

					Liquid drawLiquid = Liquids.water;
					if (front != null && front.pressure().getMain() != null) {
						drawLiquid = front.pressure().current;
					} else if (back != null && back.pressure().getMain() != null) {
						drawLiquid = back.pressure().current;
					}

					int frame = drawLiquid.getAnimationFrame();
					int gas = drawLiquid.gas ? 1 : 0;

					float xscl = Draw.xscl, yscl = Draw.yscl;
					Draw.scl(1f, 1f);
					Drawf.liquid(liquidRegions[gas][frame], x, y, smoothAlpha, tmpColor);
					Draw.scl(xscl, yscl);
				}
				Draw.rect(arrowRegion, x, y, rotdeg());
			}
			Draw.rect(tiles[tiling], x, y, rot);
			if (tiling == 0) Draw.rect(topRegion, x, y, rotdeg());
		}

		/**
		 * Returns the building at the start of the pump chain.
		 */
		public @Nullable HasPressure getFrom() {
			PressureLiquidPumpBuild last = this;
			HasPressure out = back() instanceof HasPressure back ? back.getPressureDestination(last, 0) : null;
			while (out instanceof PressureLiquidPumpBuild pump) {
				if (!pump.connected(last)) return null;
				last = pump;
				out = pump.back() instanceof HasPressure back ? back.getPressureDestination(last, 0) : null;
			}
			return (out != null && out.connected(last)) ? out : null;
		}
		/**
		 * Returns the building at the end of the pump chain.
		 */
		public @Nullable HasPressure getTo() {
			PressureLiquidPumpBuild last = this;
			HasPressure out = front() instanceof HasPressure front ? front.getPressureDestination(last, 0) : null;
			while (out instanceof PressureLiquidPumpBuild pump) {
				if (!pump.connected(last)) return null;
				last = pump;
				out = pump.front() instanceof HasPressure front ? front.getPressureDestination(last, 0) : null;
			}
			return (out != null && out.connected(last)) ? out : null;
		}

		@Override
		public void onProximityUpdate() {
			super.onProximityUpdate();

			tiling = 0;
			boolean inverted = rotation == 1 || rotation == 2;
			if (front() instanceof HasPressure front && connected(front)) tiling |= inverted ? 2 : 1;
			if (back() instanceof HasPressure back && connected(back)) tiling |= inverted ? 1 : 2;

			new PressureSection().mergeFlood(this);
		}

		@Override public boolean outputsPressurizedFluid(HasPressure to, @Nullable Liquid liquid, float amount) {
			return false;
		}

		@Override public PressureModule pressure() {
			return pressure;
		}
		@Override public PressureConfig pressureConfig() {
			return pressureConfig;
		}

		@Override
		public void read(Reads read, byte revision) {
			super.read(read, revision);
			pressure.read(read);
			filter = read.i();
			smoothAlpha = read.f();
		}

		@Override
		public void updateTile() {
			super.updateTile();
			if (efficiency > 0) {
				HasPressure front = getTo();
				HasPressure back = getFrom();

				@Nullable Liquid pumpLiquid = configurable ? Vars.content.liquid(filter) : (back == null ? null : back.pressure().getMain());

				float frontPressure = front == null ? 0 : front.pressure().getPressure(pumpLiquid);
				float backPressure = back == null ? 0 : back.pressure().getPressure(pumpLiquid);

				float flow = pumpStrength/chainSize() * ((backPressure + pressureDifference * chainSize()) - frontPressure) / OlLiquids.getViscosity(pumpLiquid);

				if (pumpLiquid != null && front != null && back != null) {
					flow = Mathf.clamp(flow, -front.pressure().get(pumpLiquid), back.pressure().get(pumpLiquid));
				}

				if (
					front == null || back == null ||
					(front.acceptsPressurizedFluid(back, pumpLiquid, flow) &&
					back.outputsPressurizedFluid(front, pumpLiquid, flow))
				) {
					if (front != null) front.addFluid(pumpLiquid, flow);
					if (back != null) back.removeFluid(pumpLiquid, flow);
				}
			}
		}

		@Override
		public void write(Writes write) {
			super.write(write);
			pressure.write(write);
			write.i(filter);
			write.f(smoothAlpha);
		}
	}
}
