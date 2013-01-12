package fridayfun;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import CTFApi.CaptureTheFlagApi;
import robocode.*;
import robocode.util.Utils;

public class Zeus extends CaptureTheFlagApi {

    int botNumber = 0;
    int takingFlagBot = 5;
    int takingMiddle = 3;
    boolean flagOwned = false;
    boolean iOwnFlag = false;
    boolean startLeft = false;
    boolean newCheckpoints = false;
    boolean robotsStatus[] = new boolean[]{true, true, true, true, true};
    List<Point2D> checkPoints;
    List<Integer> OFFENCE = new ArrayList<Integer>() {
        private static final long serialVersionUID = 1L;

        {
            add(5);
            add(4);
            add(2);
            add(3);
        }
    };
    List<Integer> DEFENSE = new ArrayList<Integer>() {
        private static final long serialVersionUID = 1L;

        {
            add(1);
        }
    };

    /**
     * Note that CaptureTheFlagApi inherits TeamRobot, so users can directly use
     * functions of TeamRobot.
     */
    /**
     * run: Zeus's default behavior
     */
    public void run() {
        registerMe();
        setColors(Color.RED, Color.BLACK, Color.BLACK, Color.ORANGE,
                Color.LIGHT_GRAY);

        botNumber = getBotNumber(getName());

        if (getX() < getBattleFieldWidth() / 2) {
            startLeft = true;
        }

        findWay();

        if (botNumber == 4 || botNumber == 3) {
            waitForNumberOfTurns(20);
        }

        while (true) {
            newCheckpoints = false;

            stop();
            for (int i = 0; i < checkPoints.size(); i++) {
                if (newCheckpoints) {
                    break;
                }
                nextMove(checkPoints.get(i));
            }

            while (!flagOwned && !newCheckpoints || checkPoints.size() == 0) {
                setTurnGunLeft(360);
                turnGunLeft(360);
            }
        }
    }

    /**
     * onScannedRobot: What to do when you see another robot
     */
    public void onScannedRobot(ScannedRobotEvent e) {
        if (!this.isTeammate(e.getName())) {

            //Turn to meet the scanned robot
            setTurnGunLeft(getHeading() - getGunHeading() + e.getBearing());

            // DEFENCE max = 5, OFFENCE max = 2.5
            double maxPower = DEFENSE.contains(getBotNumber(getName())) ? 5 : 2.5;

            // calculate fire power based on distance; DAMAGE = 4 * firePower.
            double firePower = Math.min(500 / e.getDistance(), maxPower);

            // if the gun is cool and we're pointed in the right direction and velocity of robot is less than 4.5 (max is 8), shoot!
            if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 5 && e.getVelocity() < 4.5) {
                setFire(firePower);
            }

            //Check to see where it's moved
            //setTurnRadarLeft(360);
        }
    }

    /**
     * onHitByBullet: What to do when you're hit by a bullet
     */
    public void onHitByBullet(HitByBulletEvent e) {
        // turnLeft(90 - e.getBearing());
    }

    @Override
    public void onHitObject(HitObjectEvent event) {
        if (event.getType().equals("flag") && OFFENCE.contains(botNumber)) {
            iOwnFlag = true;
            flagOwned = true;
            try {
                broadcastMessage(new GotFlagMessage(true, botNumber));
            } catch (IOException e) {
                e.printStackTrace();
            }
            stop();
            findWay();
        }
    }

    @Override
    public void onHitObstacle(HitObstacleEvent e) {
        somethingHit(e.getBearing());
    }

    @Override
    public void onHitRobot(HitRobotEvent event) {
        somethingHit(event.getBearing());
    }

    private void somethingHit(double bearing) {
        stop();

        if (!iOwnFlag && botNumber == takingFlagBot) {
            try {
                broadcastMessage(new IsCourierChangeNeededMessage(takingFlagBot, getDistanceToEnemyFlag(getX(), getY())));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        if ((0 <= getHeading() && getHeading() < 90)
                || (180 <= getHeading() && getHeading() < 270)) {
            setTurnRight(bearing - 60);
        } else if ((90 <= getHeading() && getHeading() < 180)
                || (270 <= getHeading() && getHeading() < 360)) {
            setTurnRight(bearing + 60);
        }
        back(100);
        findWay();
    }

    public void onHitWall(HitWallEvent e) {
    }

    @Override
    public void onRobotDeath(RobotDeathEvent event) {
        if (isTeammate(event.getName())) {
            int deadRobotNumber = getBotNumber(event.getName());
            System.out.println("Robot no " + deadRobotNumber + " died");
            robotsStatus[deadRobotNumber - 1] = false;
            if (deadRobotNumber == takingFlagBot) {
                System.out.println("Flag dropped down by " + deadRobotNumber);
                for (int i = 0; i < robotsStatus.length; i++) {
                    if (robotsStatus[i] && OFFENCE.contains(i + 1)) {
                        takingFlagBot = i + 1;
                        System.out.println("Courier has changed to " + takingFlagBot);
                        break;
                    }
                }
            }
            stop();
            findWay();
        }
    }

    public void onScannedObject(ScannedObjectEvent e) {
        if (e.getObjectType().equals("flag")) {
            e.getBearing();
        }
    }

    @Override
    public void onMessageReceived(MessageEvent event) {
        if (event.getMessage() instanceof GotFlagMessage) {
            flagOwned = ((GotFlagMessage) event.getMessage()).getGotFlag();
            if (flagOwned) {
                System.out.println("Flag picked up by " + takingFlagBot);
            } else {
                System.out.println("Flag dropped down by " + takingFlagBot);
            }
            stop();
            findWay();
        } else if (event.getMessage() instanceof CourierHasChangedMessage) {
            takingFlagBot = ((CourierHasChangedMessage) event.getMessage()).getNewCourier();
            System.out.println("Courier has changed to " + takingFlagBot);
            stop();
            findWay();
        } else if (event.getMessage() instanceof IsCourierChangeNeededMessage) {
            IsCourierChangeNeededMessage cMsg = (IsCourierChangeNeededMessage) event.getMessage();
            // Only check for change if the courier hasn't changed already
            if (cMsg.getMessageSender() == takingFlagBot) {
                if (cMsg.getDistanceToEnemyFlag() > getDistanceToEnemyFlag(getX(), getY())) {
                    try {
                        broadcastMessage(new CourierHasChangedMessage(botNumber));
                        takingFlagBot = botNumber;
                        stop();
                        findWay();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    private void nextMove(Point2D point) {
        double x = point.getX() - getX();
        double y = point.getY() - getY();

        double goAngle = Utils.normalRelativeAngle(Math.atan2(x, y)
                - getHeadingRadians());

        setTurnGunLeft(360);
        turnRightRadians(goAngle);
        ahead(getLocation().distance(point));
    }

    private Point2D getLocation() {
        return new Point2D.Double(getX(), getY());
    }

    private int getBotNumber(String name) {
        String s = "0";
        int first = name.indexOf("(") + 1;
        int last = name.lastIndexOf(")");

        if (first >= 0 && last >= 0) {
            s = name.substring(first, last);
        }

        int number = Integer.parseInt(s);
        return number <= 5 ? number : number - 5;
    }

    private void findWay() {
        checkPoints = new ArrayList<Point2D>();
        if (OFFENCE.contains(botNumber)) {
            if (!flagOwned) {
                checkPoints = new ArrayList<Point2D>();
                if (getY() >= getBattleFieldHeight() / 2) {
                    if (areInOpositeHalfs(getLocation(), getEnemyFlag())) {
                        checkPoints.addAll(getTopEntranceCheckpoints());
                    }

                    if (isEnemyFlagInBase()) {
                        checkPoints.add(getTopEnemyBaseCheckpoint());
                        if (botNumber == takingFlagBot) {
                            if (getX() > getEnemyBase().getMinX() && getX() < getEnemyBase().getMinX() + getBaseWidth()) {
                                checkPoints.remove(checkPoints.size() - 1);
                            }

                            checkPoints.add(getTopEnemyBaseEntranceCheckpoint());
                        }
                    }

                    if (botNumber == takingFlagBot) {
                        checkPoints.add(getEnemyFlag());
                    }

                } else {
                    if (areInOpositeHalfs(getLocation(), getEnemyFlag())) {
                        checkPoints.addAll(getBottomEntranceCheckpoints());
                    }

                    if (isEnemyFlagInBase()) {
                        checkPoints.add(getBottomEnemyBaseCheckpoint());
                        if (botNumber == takingFlagBot) {
                            if (getX() > getEnemyBase().getMinX() && getX() < getEnemyBase().getMinX() + getBaseWidth()) {
                                checkPoints.remove(checkPoints.size() - 1);
                            }

                            checkPoints.add(getBottomEnemyBaseEntranceCheckpoint());
                        }
                    }

                    if (botNumber == takingFlagBot) {
                        checkPoints.add(getEnemyFlag());
                    } else if (botNumber == takingMiddle) {
                        checkPoints.remove(checkPoints.size() - 1);
                        checkPoints.add(getCenterCheckpoint());
                    }
                }
            } else {
                if (getY() > getBattleFieldHeight() / 2
                        && botNumber != takingMiddle) {
                    if (isPointInEnemyBase(getLocation())) {
                        checkPoints.add(getTopEnemyBaseEntranceCheckpoint());
                    }

                    if (!((startLeft && getX() < getBattleFieldWidth() / 2) || (!startLeft && getX() > getBattleFieldWidth() / 2))) {
                        List<Point2D> points = getTopEntranceCheckpoints();
                        Collections.reverse(points);
                        checkPoints.addAll(points);
                    }

                    if (botNumber == takingFlagBot && iOwnFlag) {
                        checkPoints.add(getTopOwnBaseEntranceCheckpoint());
                        checkPoints.add(getOwnFlag());
                    } else {
                        checkPoints.add(getTopOwnBaseCheckpoint());
                    }

                } else {
                    if (isPointInEnemyBase(getLocation())) {
                        checkPoints.add(getBottomEnemyBaseEntranceCheckpoint());
                    }

                    if (!((startLeft && getX() < getBattleFieldWidth() / 2) || (!startLeft && getX() > getBattleFieldWidth() / 2))) {
                        List<Point2D> points = getBottomEntranceCheckpoints();
                        Collections.reverse(points);
                        checkPoints.addAll(points);
                    }
                    if (botNumber == takingFlagBot && iOwnFlag) {
                        checkPoints.add(getBottomOwnBaseEntranceCheckpoint());
                        checkPoints.add(getOwnFlag());
                    } else {
                        checkPoints.add(getBottomOwnBaseCheckpoint());
                    }
                }
            }
        } else {
            // Defense movement
            checkPoints.add(getTopOwnBaseCheckpoint());
            checkPoints.add(getTopOwnBaseEntranceCheckpoint());
            checkPoints.add(getOwnFlag());
        }
        newCheckpoints = true;
    }

    private Point2D getTopEnemyBaseCheckpoint() {
        if (startLeft) {
            return getTopLeftBaseCheckpoint();
        } else {
            return getTopRightBaseCheckpoint();
        }
    }

    private Point2D getTopOwnBaseCheckpoint() {
        if (!startLeft) {
            return getTopLeftBaseCheckpoint();
        } else {
            return getTopRightBaseCheckpoint();
        }
    }

    private Point2D getTopLeftBaseCheckpoint() {
        return new Point2D.Double(getBattleFieldWidth() - getBaseWidth(),
                getBattleFieldHeight() / 2 + getBaseHeight() - 40);
    }

    private Point2D getTopRightBaseCheckpoint() {
        return new Point2D.Double(getBaseWidth() + 60, getBattleFieldHeight()
                / 2 + getBaseHeight() - 40);
    }

    private Point2D getTopEnemyBaseEntranceCheckpoint() {
        return new Point2D.Double(getEnemyFlag().getX(), getBattleFieldHeight()
                / 2 + getBaseHeight() - 40);
    }

    private Point2D getTopOwnBaseEntranceCheckpoint() {
        return new Point2D.Double(getOwnFlag().getX(), getBattleFieldHeight()
                / 2 + getBaseHeight() - 40);
    }

    private Point2D getBottomOwnBaseEntranceCheckpoint() {
        return new Point2D.Double(getOwnFlag().getX(), getBattleFieldHeight()
                / 2 - getBaseHeight() + 40);
    }

    private Point2D getBottomEnemyBaseCheckpoint() {
        if (startLeft) {
            return getBottomLeftBaseCheckpoint();
        } else {
            return getBottomRightBaseCheckpoint();
        }
    }

    private Point2D getBottomOwnBaseCheckpoint() {
        if (!startLeft) {
            return getBottomLeftBaseCheckpoint();
        } else {
            return getBottomRightBaseCheckpoint();
        }
    }

    private Point2D getBottomLeftBaseCheckpoint() {
        return new Point2D.Double(getBattleFieldWidth()
                - getEnemyBase().getHeight(), getBattleFieldHeight() / 2
                - getEnemyBase().getWidth() + 40);
    }

    private Point2D getBottomRightBaseCheckpoint() {
        return new Point2D.Double(getEnemyBase().getHeight() + 60,
                getBattleFieldHeight() / 2 - getEnemyBase().getWidth() + 40);
    }

    private Point2D getBottomEnemyBaseEntranceCheckpoint() {
        return new Point2D.Double(getEnemyFlag().getX(), getBattleFieldHeight()
                / 2 - getEnemyBase().getWidth() + 40);
    }

    private Point2D getCenterCheckpoint() {
        if (startLeft) {
            return new Point2D.Double(getBattleFieldWidth()
                    - getEnemyBase().getHeight() - 60,
                    getBattleFieldHeight() / 2);
        } else {
            return new Point2D.Double(getEnemyBase().getHeight() + 60,
                    getBattleFieldHeight() / 2);
        }
    }

    private List<Point2D> getTopEntranceCheckpoints() {
        List<Point2D> topCheckpoints = new ArrayList<Point2D>();
        topCheckpoints.add(new Point2D.Double(getBattleFieldWidth() / 2 - 50,
                getBattleFieldHeight() - 50));
        topCheckpoints.add(new Point2D.Double(getBattleFieldWidth() / 2 + 50,
                getBattleFieldHeight() - 50));
        if (!startLeft) {
            Collections.reverse(topCheckpoints);
        }
        return topCheckpoints;
    }

    private List<Point2D> getBottomEntranceCheckpoints() {
        List<Point2D> bottomCheckpoints = new ArrayList<Point2D>();
        bottomCheckpoints.add(new Point2D.Double(
                getBattleFieldWidth() / 2 - 50, 50.0));
        bottomCheckpoints.add(new Point2D.Double(
                getBattleFieldWidth() / 2 + 50, 50.0));
        if (!startLeft) {
            Collections.reverse(bottomCheckpoints);
        }
        return bottomCheckpoints;
    }

    private void waitForNumberOfTurns(int numberOfTurns) {
        for (int i = 0; i < numberOfTurns; i++) {
            doNothing();
        }
    }

    private boolean areInOpositeHalfs(Point2D p1, Point2D p2) {
        if (p1.getX() < getBattleFieldWidth() / 2
                && p2.getX() > getBattleFieldWidth() / 2) {
            return true;
        } else if (p2.getX() < getBattleFieldWidth() / 2
                && p1.getX() > getBattleFieldWidth() / 2) {
            return true;
        }

        return false;
    }

    private double getBaseWidth() {
        return getOwnBase().getHeight();
    }

    private double getBaseHeight() {
        return getOwnBase().getWidth();
    }

    private boolean isEnemyFlagInBase() {
        return isPointInEnemyBase(getEnemyFlag());
    }

    private boolean isPointInEnemyBase(Point2D p1) {
        if (p1.getX() > getEnemyBase().getMinX()
                && p1.getX() < getEnemyBase().getMinX() + getBaseWidth()
                && p1.getY() > getEnemyBase().getMinY()
                && p1.getY() < getEnemyBase().getMinY() + getBaseHeight()) {
            return true;
        }
        return false;
    }

    private double getDistanceToEnemyFlag(double x, double y) {
        Point2D flag = getEnemyFlag();
        return (Math.sqrt(Math.pow(Math.abs(x - flag.getX()), 2) + Math.pow(Math.abs(y - flag.getY()), 2)));
    }
}

class GotFlagMessage implements Serializable {

    private static final long serialVersionUID = 1L;
    private boolean gotFlag;
    private int courier;

    public GotFlagMessage(Boolean gotFlag, int courier) {
        this.gotFlag = gotFlag;
        this.courier = courier;
    }

    public Boolean getGotFlag() {
        return gotFlag;
    }

    public void setGotFlag(Boolean gotFlag) {
        this.gotFlag = gotFlag;
    }

    public int getCourier() {
        return courier;
    }
}

class IsCourierChangeNeededMessage implements Serializable {

    private static final long serialVersionUID = 1L;
    private double distanceToEnemyFlag;
    private int messageSender;

    public IsCourierChangeNeededMessage(int courierNumber, double distanceToEnemyFlag) {
        this.distanceToEnemyFlag = distanceToEnemyFlag;
        this.messageSender = courierNumber;
    }

    public double getDistanceToEnemyFlag() {
        return distanceToEnemyFlag;
    }

    public int getMessageSender() {
        return messageSender;
    }
}

class CourierHasChangedMessage implements Serializable {

    private static final long serialVersionUID = 1L;
    private int newCourier;

    public CourierHasChangedMessage(int newCourier) {
        this.newCourier = newCourier;
    }

    public int getNewCourier() {
        return newCourier;
    }
}