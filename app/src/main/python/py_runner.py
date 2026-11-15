import base64
import io
import json
import os
import sys
import tempfile
import time
import traceback

MAX_IMAGES = 8


def _png_b64(image):
    """Encode a PIL image as a Base64 PNG string."""
    if image.mode not in ("1", "L", "LA", "RGB", "RGBA", "P"):
        image = image.convert("RGBA")
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return base64.b64encode(buffer.getvalue()).decode("ascii")


def run_user_code(code, stdin_text):
    """Execute user code with captured stdin/stdout/stderr and return a JSON string.

    The result has the keys success, stdout, stderr, elapsedMs and images, where
    images is a list of Base64 PNG strings produced by matplotlib's plt.show() or
    Pillow's Image.show().
    """
    old_stdin, old_stdout, old_stderr = sys.stdin, sys.stdout, sys.stderr
    out_buf = io.StringIO()
    err_buf = io.StringIO()
    images = []
    restore = []
    success = True
    started = time.time()

    sys.stdin = io.StringIO(stdin_text or "")
    sys.stdout = out_buf
    sys.stderr = err_buf
    try:
        text = code or ""

        if "matplotlib" in text or "pyplot" in text:
            try:
                os.environ.setdefault("MPLCONFIGDIR", os.path.join(tempfile.gettempdir(), "mplconfig"))
                import matplotlib
                matplotlib.use("Agg", force=True)
                import matplotlib.pyplot as plt

                plt.close("all")

                def capture_figures(*_args, **_kwargs):
                    for number in plt.get_fignums():
                        if len(images) >= MAX_IMAGES:
                            break
                        figure = plt.figure(number)
                        buffer = io.BytesIO()
                        figure.savefig(buffer, format="png", dpi=100, bbox_inches="tight")
                        images.append(base64.b64encode(buffer.getvalue()).decode("ascii"))
                    plt.close("all")

                restore.append((plt, "show", plt.show))
                plt.show = capture_figures
                restore.append((matplotlib.figure.Figure, "show", matplotlib.figure.Figure.show))
                matplotlib.figure.Figure.show = lambda self, *a, **k: capture_figures()
                restore.append(("capture", capture_figures, None))
            except Exception:
                err_buf.write("matplotlib is not available: " + traceback.format_exc())

        if "PIL" in text or "Pillow" in text:
            try:
                from PIL import Image

                def capture_image(self, *_args, **_kwargs):
                    if len(images) < MAX_IMAGES:
                        images.append(_png_b64(self))

                restore.append((Image.Image, "show", Image.Image.show))
                Image.Image.show = capture_image
            except Exception:
                err_buf.write("Pillow is not available: " + traceback.format_exc())

        try:
            compiled = compile(text, "<editor.py>", "exec")
            namespace = {"__name__": "__main__"}
            exec(compiled, namespace)
        except SystemExit as exit_info:
            if exit_info.code not in (None, 0):
                success = False
                err_buf.write("Process exited with code {}\n".format(exit_info.code))
        except BaseException:
            success = False
            err_buf.write(traceback.format_exc())

        # Figures that were never shown are still displayed.
        for entry in restore:
            if entry[0] == "capture":
                try:
                    entry[1]()
                except Exception:
                    pass
    finally:
        for entry in reversed(restore):
            if entry[0] == "capture":
                continue
            target, name, original = entry
            try:
                setattr(target, name, original)
            except Exception:
                pass
        sys.stdin = old_stdin
        sys.stdout = old_stdout
        sys.stderr = old_stderr

    elapsed_ms = int((time.time() - started) * 1000)
    return json.dumps({
        "success": success,
        "stdout": out_buf.getvalue(),
        "stderr": err_buf.getvalue(),
        "elapsedMs": elapsed_ms,
        "images": images,
    })
