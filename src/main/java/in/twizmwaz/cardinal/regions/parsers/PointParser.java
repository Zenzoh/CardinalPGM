package in.twizmwaz.cardinal.regions.parsers;

import in.twizmwaz.cardinal.regions.Region;
import in.twizmwaz.cardinal.regions.type.PointRegion;
import org.jdom2.Element;

/**
 * Created by kevin on 11/22/14.
 */
public class PointParser {

    private double x, y, z;
    private float yaw, pitch;

    public PointParser(Element element) {
        if (element.getChildren().size() > 0) {
            PointRegion subPoint = Region.newRegion(element.getChildren().get(0)).getRandomPoint();
            this.x = subPoint.getX();
            this.y = subPoint.getY();
            this.z = subPoint.getZ();
        } else {
            this.x = Double.parseDouble(element.getText().split(",")[0]);
            this.y = Double.parseDouble(element.getText().split(",")[1]);
            this.z = Double.parseDouble(element.getText().split(",")[2]);
        }
        try {
            this.yaw = Float.parseFloat(element.getAttributeValue("yaw"));
        } catch (Exception ex) {
            this.yaw = 0F;
        }
        try {
            this.pitch = Float.parseFloat(element.getAttributeValue("pitch"));
        } catch (Exception ex) {
            this.pitch = 0F;
        }
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

}
