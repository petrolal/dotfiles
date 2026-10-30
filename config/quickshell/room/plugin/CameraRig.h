#pragma once

#include <QObject>
#include <QVector3D>
#include <QPointer>
#include <QVariantAnimation>
#include <QEasingCurve>
#include <QtQuick3D/private/qquick3dnode_p.h>
#include <qqmlregistration.h>

class CameraRig : public QObject
{
    Q_OBJECT
    QML_ELEMENT

    Q_PROPERTY(QQuick3DNode *camera READ camera WRITE setCamera NOTIFY cameraChanged)
    Q_PROPERTY(int duration READ duration WRITE setDuration NOTIFY durationChanged)
    Q_PROPERTY(bool moving READ isMoving NOTIFY movingChanged)

public:
    explicit CameraRig(QObject *parent = nullptr);

    QQuick3DNode *camera() const { return m_camera.data(); }
    void setCamera(QQuick3DNode *cam);

    int duration() const { return m_duration; }
    void setDuration(int ms);

    bool isMoving() const { return m_moving; }

    Q_INVOKABLE bool travelTo(QQuick3DNode *targetNode);
    Q_INVOKABLE bool travelToTransform(const QVector3D &targetPos, const QVector3D &targetRot);
    Q_INVOKABLE void stop();

signals:
    void cameraChanged();
    void durationChanged();
    void movingChanged();
    void travelStarted();
    void travelFinished();
    void travelProgress(qreal progress);

private slots:
    void onAnimationValueChanged(const QVariant &value);
    void onAnimationFinished();

private:
    QPointer<QQuick3DNode> m_camera;
    int m_duration = 400;
    bool m_moving = false;

    QVariantAnimation m_animator;
    QVector3D m_startPos;
    QVector3D m_targetPos;
    QVector3D m_startRot;
    QVector3D m_deltaRot;
};
