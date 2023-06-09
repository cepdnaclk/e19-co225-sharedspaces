import { createPortal } from "react-dom";
import styles from "../styles/Modal.module.scss";

export const Modal = ({ setIsOpen, isOpen, rect, modalRef, children }) => {
  const posLeft = {
    left: `calc(${rect.left}px + ${rect.width}px + 5dvh)`,
  };

  const posRight = {
    left: `calc(${rect.right}px - ${rect.width}px - 22dvw)`,
  };

  const posTop = {
    top: `calc(${rect.top}px + ${window.scrollY}px - 10%)`,
  };

  const posBottom = {
    top: `calc(${rect.bottom}px - 10dvh)`,
  };

  const posX = (rect.left / window.innerWidth) * 100 > 50 ? posRight : posLeft;
  const posY =
    (rect.top / document.documentElement.clientHeight) * 100 > 65
      ? posBottom
      : posTop;

  return createPortal(
    <div
      className={`${styles.container} ${isOpen ? styles.visible : ""}`}
      id="modal"
      style={{ ...posX, ...posY }}
      ref={modalRef}
    >
      {children}
    </div>,
    document.getElementById("portal")
  );
};

export default Modal;
