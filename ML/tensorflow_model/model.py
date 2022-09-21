
import tensorflow as tf


class YOLO_V1:
    def __init__(self, split_size, num_boxes, num_classes) -> None:

        _input = tf.keras.Input(shape=(448, 448, 3))
        z = tf.keras.layers.Conv2D(filters=64, kernel_size=7,
                                   strides=(2, 2), padding="same", activation=tf.keras.layers.LeakyReLU(.1))(_input)

        # Max pooling layer
        z = tf.keras.layers.MaxPool2D((2, 2), 2)(z)

        # Conv block
        z = tf.keras.layers.Conv2D(filters=192, kernel_size=3,
                                   strides=(1, 1), padding="same", activation=tf.keras.layers.LeakyReLU(.1))(z)
        z = tf.keras.layers.MaxPool2D((2, 2), 2)(z)
        z = tf.keras.layers.Conv2D(filters=128, kernel_size=1,
                                   strides=(1, 1), padding="same", activation=tf.keras.layers.LeakyReLU(.1))(z)
        z = tf.keras.layers.Conv2D(filters=256, kernel_size=3,
                                   strides=(1, 1), padding="same", activation=tf.keras.layers.LeakyReLU(.1))(z)
        z = tf.keras.layers.Conv2D(filters=256, kernel_size=1,
                                   strides=(1, 1), padding="same", activation=tf.keras.layers.LeakyReLU(.1))(z)
        z = tf.keras.layers.Conv2D(filters=512, kernel_size=3,
                                   strides=(1, 1), padding="same", activation=tf.keras.layers.LeakyReLU(.1))(z)

        # Max pooling layer
        z = tf.keras.layers.MaxPool2D(pool_size=(2, 2), strides=2)(z)

        # Conv block
        for i in range(4):
            z = tf.keras.layers.Conv2D(filters=256, kernel_size=1,
                                       strides=(1, 1), padding="same", activation=tf.keras.layers.LeakyReLU(.1))(z)
            z = tf.keras.layers.Conv2D(filters=512, kernel_size=3,
                                       strides=(1, 1), padding="same", activation=tf.keras.layers.LeakyReLU(.1))(z)

        z = tf.keras.layers.Conv2D(filters=512, kernel_size=1,
                                   strides=(1, 1), padding="same", activation=tf.keras.layers.LeakyReLU(.1))(z)
        z = tf.keras.layers.Conv2D(filters=1024, kernel_size=3,
                                   strides=(1, 1), padding="same", activation=tf.keras.layers.LeakyReLU(.1))(z)

        # Max pooling layer
        z = tf.keras.layers.MaxPool2D(pool_size=(2, 2), strides=2)(z)

        # Conv block
        for i in range(2):
            z = tf.keras.layers.Conv2D(filters=512, kernel_size=1,
                                       strides=(1, 1), padding="same", activation=tf.keras.layers.LeakyReLU(.1))(z)
            z = tf.keras.layers.Conv2D(filters=1024, kernel_size=3,
                                       strides=(1, 1), padding="same", activation=tf.keras.layers.LeakyReLU(.1))(z)

        z = tf.keras.layers.Conv2D(filters=1024, kernel_size=3,
                                   strides=(1, 1), padding="same", activation=tf.keras.layers.LeakyReLU(.1))(z)
        z = tf.keras.layers.Conv2D(filters=1024, kernel_size=3,
                                   strides=(2, 2), padding="same", activation=tf.keras.layers.LeakyReLU(.1))(z)
        z = tf.keras.layers.Conv2D(filters=1024, kernel_size=3,
                                   strides=(1, 1), padding="same", activation=tf.keras.layers.LeakyReLU(.1))(z)
        z = tf.keras.layers.Conv2D(filters=1024, kernel_size=3,
                                   strides=(1, 1), padding="same", activation=tf.keras.layers.LeakyReLU(.1))(z)

        # Dense block
        z = tf.keras.layers.Flatten()(z)
        z = tf.keras.layers.Dense(1024*split_size*split_size)(z)

        model = tf.keras.models.Model(inputs=_input, outputs=z)
        print(model.summary())

        return model


if __name__ == "__main__":
    yolo = YOLO_V1(10, 10, 10)
