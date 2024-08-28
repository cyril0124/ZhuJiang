package zhujiang.nhl2

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import xijiang.router.{HnIcn, RnIcn, SnIcn}
import zhujiang.{ZJBundle, ZJModule}
import zhujiang.chi._

import scala.collection.immutable.ListMap

case class CHIBundleParameters(
    nodeIdBits: Int,
    addressBits: Int,
    dataBits: Int,
    dataCheck: Boolean,
    txnIdBits: Int = 12, // TODO: 8-bit for issueB, 12-bit for issueE or higher
    dbIdBits: Int = 12
// TODO: has snoop
) {
    require(nodeIdBits >= 7 && nodeIdBits <= 11)
    require(addressBits >= 44 && addressBits <= 52)
    require(isPow2(dataBits))
    require(dataBits == 128 || dataBits == 256 || dataBits == 512)
}

object CHIBundleParameters {
    def apply(
        nodeIdBits: Int = 7,
        addressBits: Int = 44,
        dataBits: Int = 256,
        dataCheck: Boolean = false,
        txnIdBits: Int = 12,
        dbIdBits: Int = 12
    ): CHIBundleParameters = new CHIBundleParameters(
        nodeIdBits = nodeIdBits,
        addressBits = addressBits,
        dataBits = dataBits,
        dataCheck = dataCheck,
        txnIdBits = txnIdBits,
        dbIdBits = dbIdBits
    )
}

class CHIBundleREQ(params: CHIBundleParameters)(implicit p: Parameters) extends ZJBundle {
    val channelName = "'REQ' channel"

    val qos           = UInt(4.W)                 // TODO: not use?
    val tgtID         = UInt(params.nodeIdBits.W) // TODO: not use?
    val srcID         = UInt(params.nodeIdBits.W)
    val txnID         = UInt(12.W)
    val returnNID     = UInt(params.nodeIdBits.W) // TODO: not use?
    val stashNIDValid = Bool()
    val returnTxnID   = UInt(8.W)
    val opcode        = UInt(7.W)
    val size          = UInt(3.W)
    val addr          = UInt(params.addressBits.W)
    val ns            = Bool()                    // TODO: not use?
    val likelyshared  = Bool()
    val allowRetry    = Bool()
    val order         = UInt(2.W)
    val pCrdType      = UInt(4.W)
    val memAttr       = UInt(4.W)
    val snpAttr       = UInt(1.W)
    val lpID          = UInt(4.W)
    val snoopMe       = Bool()
    val expCompAck    = Bool()
    val traceTag      = Bool()
    val rsvdc         = UInt(4.W)

    def toReqFlit = {
        val reqFlit = Wire(new ReqFlit)

        reqFlit.RSVDC := this.rsvdc
        reqFlit.SecID1 := DontCare
        reqFlit.MECID := DontCare
        reqFlit.PBHA := DontCare
        reqFlit.MPAM := DontCare
        reqFlit.TraceTag := this.traceTag
        reqFlit.TagOp := DontCare
        reqFlit.ExpCompAck := this.expCompAck
        reqFlit.Excl := DontCare
        reqFlit.PGroupID := DontCare
        reqFlit.SnpAttr := this.snpAttr
        reqFlit.MemAttr := this.memAttr
        reqFlit.PCrdType := this.pCrdType
        reqFlit.Order := this.order
        reqFlit.AllowRetry := this.allowRetry
        reqFlit.LikelyShared := this.likelyshared
        reqFlit.NSE := DontCare
        reqFlit.NS := this.ns
        reqFlit.Addr := this.addr
        reqFlit.Size := this.size
        reqFlit.Opcode := this.opcode
        reqFlit.ReturnTxnID := this.returnTxnID
        reqFlit.StashNIDValid := this.stashNIDValid
        reqFlit.ReturnNID := this.returnNID
        reqFlit.TxnID := this.txnID
        reqFlit.SrcID := this.srcID
        reqFlit.TgtID := this.tgtID
        reqFlit.QoS := this.qos

        reqFlit
    }
}

class CHIBundleRSP(params: CHIBundleParameters)(implicit p: Parameters) extends ZJBundle {
    val channelName = "'RSP' channel"

    val qos      = UInt(4.W)                 // TODO: not use?
    val tgtID    = UInt(params.nodeIdBits.W) // TODO: not use?
    val srcID    = UInt(params.nodeIdBits.W)
    val txnID    = UInt(12.W)
    val opcode   = UInt(5.W)
    val respErr  = UInt(2.W)
    val resp     = UInt(3.W)
    val fwdState = UInt(3.W)                 // Used for DCT
    val dbID     = UInt(12.W)
    val pCrdType = UInt(4.W)
    val traceTag = Bool()

    def toRespFlit = {
        val respFlit = Wire(new RespFlit)

        respFlit.TraceTag := this.traceTag
        respFlit.TagOp := DontCare
        respFlit.PCrdType := this.pCrdType
        respFlit.DBID := this.dbID
        respFlit.CBusy := DontCare
        respFlit.FwdState := this.fwdState
        respFlit.Resp := this.resp
        respFlit.RespErr := this.respErr
        respFlit.Opcode := this.opcode
        respFlit.TxnID := this.txnID
        respFlit.SrcID := this.srcID
        respFlit.TgtID := this.tgtID
        respFlit.QoS := this.qos

        respFlit
    }

    def fromRespFlit(in: UInt) = {
        require(in.getWidth == respFlitBits)
        val respFlit = in.asTypeOf(new RespFlit)
        val rsp = Wire(new CHIBundleRSP(params))

        rsp.qos := respFlit.QoS
        rsp.tgtID := respFlit.TgtID
        rsp.srcID := respFlit.SrcID
        rsp.txnID := respFlit.TxnID
        rsp.opcode := respFlit.Opcode
        rsp.respErr := respFlit.RespErr
        rsp.resp := respFlit.Resp
        rsp.fwdState := respFlit.FwdState
        rsp.dbID := respFlit.DBID
        rsp.pCrdType := respFlit.PCrdType
        rsp.traceTag := respFlit.TraceTag

        rsp
    }

}

class CHIBundleSNP(params: CHIBundleParameters)(implicit p: Parameters) extends ZJBundle {
    val channelName = "'SNP' channel"

    val qos         = UInt(4.W)                 // TODO: not use?
    val srcID       = UInt(params.nodeIdBits.W)
    val txnID       = UInt(12.W)
    val fwdNID      = UInt(params.nodeIdBits.W) // Used for DCT
    val fwdTxnID    = UInt(12.W)                // Used for DCT
    val opcode      = UInt(5.W)
    val addr        = UInt((params.addressBits - 3).W)
    val doNotGoToSD = Bool()
    val retToSrc    = Bool()

    def fromSnoopFlit(in: UInt) = {
        require(in.getWidth == snoopFlitBits)
        val snoopFlit = in.asTypeOf(new SnoopFlit)
        val snp = Wire(new CHIBundleSNP(params))

        snp.qos := snoopFlit.QoS
        snp.srcID := snoopFlit.SrcID
        snp.txnID := snoopFlit.TxnID
        snp.fwdNID := snoopFlit.FwdNID
        snp.fwdTxnID := snoopFlit.FwdTxnID
        snp.opcode := snoopFlit.Opcode
        snp.addr := snoopFlit.Addr
        snp.doNotGoToSD := snoopFlit.DoNotGoToSD
        snp.retToSrc := snoopFlit.RetToSrc

        snp
    }
}

class CHIBundleDAT(params: CHIBundleParameters)(implicit p: Parameters) extends ZJBundle {
    val channelName = "'DAT' channel"

    val qos       = UInt(4.W)                 // TODO: not use?
    val tgtID     = UInt(params.nodeIdBits.W) // TODO: not use?
    val srcID     = UInt(params.nodeIdBits.W)
    val txnID     = UInt(12.W)
    val homeNID   = UInt(params.nodeIdBits.W) // Used for DCT
    val opcode    = UInt(4.W)
    val respErr   = UInt(2.W)
    val resp      = UInt(3.W)
    val fwdState  = UInt(3.W)                 // Used for DCT
    val dbID      = UInt(12.W)
    val ccID      = UInt(2.W)                 // TODO: not use?
    val dataID    = UInt(2.W)
    val traceTag  = Bool()
    val rsvdc     = UInt(4.W)
    val be        = UInt((params.dataBits / 8).W)
    val data      = UInt(params.dataBits.W)
    val dataCheck = if (params.dataCheck) Some(UInt((params.dataBits / 8).W)) else None
    val poison    = if (params.dataCheck) Some(UInt((params.dataBits / 64).W)) else None

    def toDataFlit = {
        val dataFlit = Wire(new DataFlit)

        dataFlit.Poison := this.poison.getOrElse(0.U)
        dataFlit.DataCheck := this.dataCheck.getOrElse(0.U)
        dataFlit.Data := this.data
        dataFlit.BE := this.be
        dataFlit.RSVDC := this.rsvdc
        dataFlit.Replicate := DontCare
        dataFlit.NumDat := DontCare
        dataFlit.CAH := DontCare
        dataFlit.TraceTag := this.traceTag
        dataFlit.TU := DontCare
        dataFlit.Tag := DontCare
        dataFlit.TagOp := DontCare
        dataFlit.DataID := this.dataID
        dataFlit.CCID := this.ccID
        dataFlit.DBID := this.dbID
        dataFlit.CBusy := DontCare
        dataFlit.DataPull := this.data
        dataFlit.DataSource := this.data
        dataFlit.Resp := this.resp
        dataFlit.RespErr := this.respErr
        dataFlit.Opcode := this.opcode
        dataFlit.HomeNID := this.homeNID
        dataFlit.TxnID := this.txnID
        dataFlit.SrcID := this.srcID
        dataFlit.TgtID := this.tgtID
        dataFlit.QoS := this.qos

        dataFlit
    }

    def fromDataFlit(in: UInt) = {
        require(in.getWidth == dataFlitBits)
        val dataFlit = in.asTypeOf(new DataFlit())
        val dat = Wire(new CHIBundleDAT(params))

        dat.qos := dataFlit.QoS
        dat.tgtID := dataFlit.TgtID
        dat.srcID := dataFlit.SrcID
        dat.txnID := dataFlit.TxnID
        dat.homeNID := dataFlit.HomeNID
        dat.opcode := dataFlit.Opcode
        dat.respErr := dataFlit.RespErr
        dat.resp := dataFlit.Resp
        dat.fwdState := dataFlit.FwdState
        dat.dbID := dataFlit.DBID
        dat.ccID := dataFlit.CCID
        dat.dataID := dataFlit.DataID
        dat.traceTag := dataFlit.TraceTag
        dat.rsvdc := dataFlit.RSVDC
        dat.be := dataFlit.BE
        dat.data := dataFlit.Data
        if(params.dataCheck){
            dat.dataCheck.get := dataFlit.DataCheck
            dat.poison.get := dataFlit.Poison
        }

        dat
    }

}

class CHIBundleDecoupled(params: CHIBundleParameters)(implicit p: Parameters) extends ZJBundle {
    val txreq = Decoupled(UInt(new CHIBundleREQ(params).getWidth.W))
    val txdat = Decoupled(UInt(new CHIBundleDAT(params).getWidth.W))
    val txrsp = Decoupled(UInt(new CHIBundleRSP(params).getWidth.W))

    val rxrsp = Flipped(Decoupled(UInt(new CHIBundleRSP(params).getWidth.W)))
    val rxdat = Flipped(Decoupled(UInt(new CHIBundleDAT(params).getWidth.W)))
    val rxsnp = Flipped(Decoupled(UInt(new CHIBundleSNP(params).getWidth.W)))
}

class ConnectToNHL2(params: CHIBundleParameters)(implicit p: Parameters) extends ZJModule {
    val io = IO(new Bundle {
        val fromNHL2    = Flipped(new CHIBundleDecoupled(params))
        val toRnIcn     = Flipped(new RnIcn)
    })

    io.toRnIcn.rx.req.valid := io.fromNHL2.txreq.valid
    io.toRnIcn.rx.req.bits  := io.fromNHL2.txreq.bits.asTypeOf(new CHIBundleREQ(params)).toReqFlit.asUInt
    io.fromNHL2.txreq.ready := io.toRnIcn.rx.req.ready

    io.toRnIcn.rx.resp.valid := io.fromNHL2.txrsp.valid
    io.toRnIcn.rx.resp.bits := io.fromNHL2.txrsp.bits.asTypeOf(new CHIBundleRSP(params)).toRespFlit.asUInt
    io.fromNHL2.txrsp.ready := io.toRnIcn.rx.resp.ready

    io.toRnIcn.rx.data.valid := io.fromNHL2.txdat.valid
    io.toRnIcn.rx.data.bits := io.fromNHL2.txdat.bits.asTypeOf(new CHIBundleDAT(params)).toDataFlit.asUInt
    io.fromNHL2.txdat.ready := io.toRnIcn.rx.data.ready

    io.fromNHL2.rxsnp.valid := io.toRnIcn.tx.snoop.valid
    io.fromNHL2.rxsnp.bits := io.fromNHL2.rxsnp.bits.asTypeOf(new CHIBundleSNP(params)).fromSnoopFlit(io.toRnIcn.tx.snoop.bits).asUInt
    io.toRnIcn.tx.snoop.ready := io.fromNHL2.rxsnp.ready

    io.fromNHL2.rxrsp.valid := io.toRnIcn.tx.resp.valid
    io.fromNHL2.rxrsp.bits := io.fromNHL2.rxrsp.bits.asTypeOf(new CHIBundleRSP(params)).fromRespFlit(io.toRnIcn.tx.resp.bits).asUInt
    io.toRnIcn.tx.resp.ready := io.fromNHL2.rxrsp.ready

    io.fromNHL2.rxdat.valid := io.toRnIcn.tx.data.valid
    io.fromNHL2.rxdat.bits := io.fromNHL2.rxdat.bits.asTypeOf(new CHIBundleDAT(params)).fromDataFlit(io.toRnIcn.tx.data.bits).asUInt
    io.toRnIcn.tx.data.ready := io.fromNHL2.rxdat.ready

}

