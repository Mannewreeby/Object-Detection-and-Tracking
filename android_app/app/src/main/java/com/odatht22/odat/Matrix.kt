package com.odatht22.odat

class Matrix1D(
    val v:Array<Int>,
    val nx:Int,
    val offset:Int,
    val xstride:Int) {
    // TODO: Check that the nx,offset,strides etc are valid

    constructor(nx:Int) : this(Array(nx,{i->0}), nx, 0, 1) {

    }

    fun offsetof(ix:Int):Int {
        return offset + ix*xstride
    }

    operator fun get(ix:Int): Int {
        return v[offsetof(ix)]
    }

    operator fun set(ix:Int, v:Int) {
        this.v[offsetof(ix)] = v
    }

    fun reverse() : Matrix1D {
        return Matrix1D(v, nx, offsetof(nx-1), -xstride)
    }

    fun submatrix(startx:Int, newNX:Int) : Matrix1D {
        return Matrix1D(v,newNX,offsetof(startx), xstride)
    }

    fun transform(body: (Int, Int) -> Int ) {
        for(ix in 0..(nx-1)){
            this[ix] = body(this[ix], ix)
        }
    }

    fun bake() : Matrix1D {
        val rv = Matrix1D(nx);
        for(ix in 0..(nx-1)) {
            rv[ix] = this[ix]
        }
        return rv
    }
}



class Matrix2D(
    val v:Array<Int>,
    val nx:Int, val ny:Int,
    val offset:Int,
    val xstride:Int, val ystride:Int) {
    // TODO: Check that the nx,ny,offset,strides etc are valid

    constructor(nx:Int, ny:Int) : this(Array(nx*ny,{i->0}), nx, ny, 0, 1, nx ) {

    }

    fun offsetof(ix:Int,iy:Int): Int {
        return offset + ix*xstride + iy*ystride
    }

    operator fun get(ix:Int,iy:Int): Int {
        return v[offsetof(ix,iy)]
    }

    operator fun set(ix:Int,iy:Int,v:Int) {
        this.v[offsetof(ix,iy)] = v
    }

    operator fun get(ix:Int): Matrix1D {
        return Matrix1D(v, ny, offsetof(ix,0), ystride)
    }

    fun transpose(): Matrix2D {
        return Matrix2D(v,ny,nx,offset,ystride,xstride)
    }

    fun submatrix(startx:Int, starty:Int, newNX:Int, newNY:Int) : Matrix2D {
        return Matrix2D(v,newNX,newNY,offsetof(startx,starty), xstride, ystride)
    }

    fun transform(body: (Int, Int, Int) -> Int ) {
        for(iy in 0..(ny-1)) {
            for(ix in 0..(nx-1)){
                this[ix,iy] = body(this[ix,iy], ix,iy)
            }
        }
    }

    fun bake() : Matrix2D {
        val rv = Matrix2D(nx,ny);
        for(ix in 0..(nx-1)) {
            for(iy in 0..(ny-1)) {
                rv[ix,iy] = this[ix,iy]
            }
        }
        return rv
    }
}


class Matrix3D(
    val v:Array<Int>,
    val nx:Int, val ny:Int, val nz:Int,
    val offset:Int,
    val xstride:Int, val ystride:Int, val zstride:Int) {
    // TODO: Check that the nx,ny,nz,offset,strides etc are valid

    constructor(nx:Int, ny:Int, nz:Int) : this(Array(nx*ny*nz, { i -> 0 }), nx, ny, nz, 0, 1, nx, nx*ny ) {

    }
    operator fun get(ix:Int,iy:Int,iz:Int): Int {
        return v[offset + ix*xstride + iy*ystride + iz*zstride]
    }

    operator fun set(ix:Int,iy:Int,iz:Int, v:Int) {
        this.v[offset + ix*xstride + iy*ystride + iz*zstride] = v
    }

    operator fun get(ix:Int): Matrix2D {
        return Matrix2D(v, ny, nz, offset + ix*xstride, ystride, zstride )
    }

    fun transform(body: (Int, Int, Int, Int) -> Int ) {
        for(iz in 0..(nz-1)) {
            for(iy in 0..(ny-1)) {
                for(ix in 0..(nx-1)){
                    this[ix,iy,iz] = body(this[ix,iy,iz], ix,iy,iz)
                }
            }
        }
    }

    fun bake() : Matrix3D {
        val rv = Matrix3D(nx,ny,nz);
        for(ix in 0..(nx-1)) {
            for(iy in 0..(ny-1)) {
                for(iz in 0..(nz-1)){
                    rv[ix,iy,iz] = this[ix,iy,iz]
                }
            }
        }
        return rv
    }
}