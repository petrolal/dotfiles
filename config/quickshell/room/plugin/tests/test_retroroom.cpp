#include <QTest>
#include <QSignalSpy>
#include <QGuiApplication>
#include "../SceneAnchorRegistry.h"
#include "../CameraRig.h"
#include "../RoomSceneController.h"

class TestRetroRoom : public QObject
{
    Q_OBJECT

private slots:
    void testAnchorRegistry();
    void testCameraRigSettings();
    void testRoomControllerFlow();
    void testRoomControllerRejectionWhenBusy();
};

void TestRetroRoom::testAnchorRegistry()
{
    SceneAnchorRegistry registry;
    QCOMPARE(registry.hasAnchor("home"), false);

    QQuick3DNode homeNode;
    homeNode.setObjectName("homeNode");
    QVERIFY(registry.registerAnchor("home", &homeNode));
    QCOMPARE(registry.hasAnchor("home"), true);
    QCOMPARE(registry.anchorNode("home"), &homeNode);

    QQuick3DNode terminalNode;
    QVERIFY(registry.registerAnchor("terminal", &terminalNode));
    QCOMPARE(registry.registeredAnchors().contains("terminal"), true);

    QVERIFY(registry.unregisterAnchor("home"));
    QCOMPARE(registry.hasAnchor("home"), false);
}

void TestRetroRoom::testCameraRigSettings()
{
    CameraRig rig;
    QCOMPARE(rig.duration(), 400);

    rig.setDuration(800);
    QCOMPARE(rig.duration(), 800);

    QCOMPARE(rig.isMoving(), false);
}

void TestRetroRoom::testRoomControllerFlow()
{
    RoomSceneController controller;
    SceneAnchorRegistry registry;
    CameraRig rig;

    QQuick3DNode homeNode;
    QQuick3DNode termNode;
    termNode.setPosition(QVector3D(10, 5, -20));

    QQuick3DNode cameraNode;
    cameraNode.setPosition(QVector3D(0, 0, 0));
    rig.setCamera(&cameraNode);
    rig.setDuration(50); // fast for testing

    registry.registerAnchor("home", &homeNode);
    registry.registerAnchor("terminal", &termNode);

    controller.setRegistry(&registry);
    controller.setCameraRig(&rig);

    QCOMPARE(controller.currentAnchor(), QStringLiteral("home"));
    QCOMPARE(controller.isTransitioning(), false);

    QSignalSpy completedSpy(&controller, &RoomSceneController::travelCompleted);
    QVERIFY(controller.travelTo("terminal"));
    QCOMPARE(controller.isTransitioning(), true);
    QCOMPARE(controller.targetAnchor(), QStringLiteral("terminal"));

    // Wait for the animation to finish
    QVERIFY(completedSpy.wait(500));
    QCOMPARE(controller.isTransitioning(), false);
    QCOMPARE(controller.currentAnchor(), QStringLiteral("terminal"));
}

void TestRetroRoom::testRoomControllerRejectionWhenBusy()
{
    RoomSceneController controller;
    SceneAnchorRegistry registry;
    CameraRig rig;

    QQuick3DNode homeNode;
    QQuick3DNode termNode;
    QQuick3DNode musicNode;

    QQuick3DNode cameraNode;
    rig.setCamera(&cameraNode);
    rig.setDuration(500);

    registry.registerAnchor("home", &homeNode);
    registry.registerAnchor("terminal", &termNode);
    registry.registerAnchor("music", &musicNode);

    controller.setRegistry(&registry);
    controller.setCameraRig(&rig);

    QVERIFY(controller.travelTo("terminal"));
    QCOMPARE(controller.isTransitioning(), true);

    QSignalSpy rejectSpy(&controller, &RoomSceneController::travelRejected);
    // Attempting another travel while transitioning should fail
    bool ok = controller.travelTo("music");
    QCOMPARE(ok, false);
    QCOMPARE(rejectSpy.count(), 1);
    QCOMPARE(rejectSpy.takeFirst().at(0).toString(), QStringLiteral("BUSY"));
}

QTEST_MAIN(TestRetroRoom)
#include "test_retroroom.moc"
