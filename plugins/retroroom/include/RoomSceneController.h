#pragma once

#include <QObject>
#include <QString>
#include <QPointer>
#include <qqmlregistration.h>

#include "SceneAnchorRegistry.h"
#include "CameraRig.h"

class RoomSceneController : public QObject
{
    Q_OBJECT
    QML_ELEMENT

    Q_PROPERTY(bool transitioning READ isTransitioning NOTIFY transitioningChanged)
    Q_PROPERTY(QString currentAnchor READ currentAnchor NOTIFY currentAnchorChanged)
    Q_PROPERTY(QString targetAnchor READ targetAnchor NOTIFY targetAnchorChanged)
    Q_PROPERTY(SceneAnchorRegistry *registry READ registry WRITE setRegistry NOTIFY registryChanged)
    Q_PROPERTY(SceneAnchorRegistry *anchorRegistry READ registry WRITE setRegistry NOTIFY registryChanged)
    Q_PROPERTY(CameraRig *cameraRig READ cameraRig WRITE setCameraRig NOTIFY cameraRigChanged)

public:
    explicit RoomSceneController(QObject *parent = nullptr);

    bool isTransitioning() const { return m_transitioning; }
    QString currentAnchor() const { return m_currentAnchor; }
    QString targetAnchor() const { return m_targetAnchor; }

    SceneAnchorRegistry *registry() const { return m_registry.data(); }
    void setRegistry(SceneAnchorRegistry *reg);

    CameraRig *cameraRig() const { return m_cameraRig.data(); }
    void setCameraRig(CameraRig *rig);

    Q_INVOKABLE bool travelTo(const QString &anchorId);
    Q_INVOKABLE void reset();

signals:
    void transitioningChanged(bool transitioning);
    void currentAnchorChanged(const QString &currentAnchor);
    void targetAnchorChanged(const QString &targetAnchor);
    void registryChanged();
    void cameraRigChanged();
    void travelCompleted(const QString &anchorId);
    void travelRejected(const QString &reason);

private slots:
    void onRigTravelFinished();

private:
    bool m_transitioning = false;
    QString m_currentAnchor = QStringLiteral("home");
    QString m_targetAnchor;

    QPointer<SceneAnchorRegistry> m_registry;
    QPointer<CameraRig> m_cameraRig;
};
