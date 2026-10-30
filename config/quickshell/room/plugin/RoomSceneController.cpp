#include "RoomSceneController.h"
#include <QDebug>

RoomSceneController::RoomSceneController(QObject *parent)
    : QObject(parent)
{
}

void RoomSceneController::setRegistry(SceneAnchorRegistry *reg)
{
    if (m_registry == reg) return;
    m_registry = reg;
    emit registryChanged();
}

void RoomSceneController::setCameraRig(CameraRig *rig)
{
    if (m_cameraRig == rig) return;

    if (m_cameraRig) {
        disconnect(m_cameraRig, &CameraRig::travelFinished, this, &RoomSceneController::onRigTravelFinished);
    }

    m_cameraRig = rig;

    if (m_cameraRig) {
        connect(m_cameraRig, &CameraRig::travelFinished, this, &RoomSceneController::onRigTravelFinished);
    }

    emit cameraRigChanged();
}

bool RoomSceneController::travelTo(const QString &anchorId)
{
    if (m_transitioning) {
        qDebug() << "[RoomSceneController] Travel rejected: currently transitioning.";
        emit travelRejected(QStringLiteral("BUSY"));
        return false;
    }

    if (!m_registry) {
        qWarning() << "[RoomSceneController] Travel rejected: no anchor registry set.";
        emit travelRejected(QStringLiteral("NO_REGISTRY"));
        return false;
    }

    if (!m_cameraRig) {
        qWarning() << "[RoomSceneController] Travel rejected: no camera rig set.";
        emit travelRejected(QStringLiteral("NO_CAMERA_RIG"));
        return false;
    }

    if (!m_registry->hasAnchor(anchorId)) {
        qWarning() << "[RoomSceneController] Travel rejected: unknown anchor" << anchorId;
        emit travelRejected(QStringLiteral("UNKNOWN_ANCHOR"));
        return false;
    }

    QQuick3DNode *targetNode = m_registry->anchorNode(anchorId);
    if (!targetNode) {
        qWarning() << "[RoomSceneController] Target node is null for anchor" << anchorId;
        emit travelRejected(QStringLiteral("NULL_NODE"));
        return false;
    }

    m_targetAnchor = anchorId;
    emit targetAnchorChanged(m_targetAnchor);

    m_transitioning = true;
    emit transitioningChanged(true);

    return m_cameraRig->travelTo(targetNode);
}

void RoomSceneController::reset()
{
    travelTo(QStringLiteral("home"));
}

void RoomSceneController::onRigTravelFinished()
{
    m_currentAnchor = m_targetAnchor;
    m_transitioning = false;

    emit currentAnchorChanged(m_currentAnchor);
    emit transitioningChanged(false);
    emit travelCompleted(m_currentAnchor);

    qDebug() << "[RoomSceneController] Travel arrived at anchor:" << m_currentAnchor;
}
