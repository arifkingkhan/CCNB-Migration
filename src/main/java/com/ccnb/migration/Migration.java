package com.ccnb.migration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.query.Query;

import com.ccnb.bean.CCNBMeterMapping;
import com.ccnb.bean.CCNBNSCStagingMigration;
import com.ccnb.bean.CTRMaster;
import com.ccnb.bean.CcnbNgbTariffMapping;
import com.ccnb.bean.MeterMaster;
import com.ccnb.bean.NSCStagingMigration;
import com.ccnb.bean.NSCStagingMigrationStatus;
import com.ccnb.bean.Zone;
import com.ccnb.util.PathUtil;

public class Migration {

	public static void main(String[] args) {

		//For creating a exception Text File
		long exceptionCount=0, recordCount=0;
		File file = new File(PathUtil.baseExceptionFolder + "CcnbMigrationExceptionLog.txt");
		FileWriter fw=null;
		BufferedWriter bw = null;
		PrintWriter writer = null;
		try	
		{
			if(file.exists()==false)
				file.createNewFile();
			else
			{
				file.delete();
				file.createNewFile();
			}
			fw = new FileWriter(file,true);
			bw = new BufferedWriter(fw);
			writer = new PrintWriter(bw);
		}
		catch(Exception e){}

		SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
		Session session = sessionFactory.openSession();
		long startTime = System.currentTimeMillis();
		
		Query<CCNBNSCStagingMigration> ccnbNscStagingMigrationQuery = session.createQuery("from CCNBNSCStagingMigration where migrated=false");
		List<CCNBNSCStagingMigration> unmigratedRecords = ccnbNscStagingMigrationQuery.list();
		
		Query<CcnbNgbTariffMapping> tariffMappingQuery = session.createQuery("from CcnbNgbTariffMapping");
		List<CcnbNgbTariffMapping> tariffMappings = tariffMappingQuery.list(); 
		
		SimpleDateFormat ccnbDateFormat = new SimpleDateFormat("dd-MM-yyyy");

		Query<String> zoneQuery;
		Query<Integer> migrationStatus;
		
		Query<CCNBMeterMapping> ccnbMeterMappingQuery = session.createQuery("from CCNBMeterMapping");
		List<CCNBMeterMapping> ccnbMeterMappings = ccnbMeterMappingQuery.list();
		
		//sa_types for unmetered connections
		ArrayList<String> unmeteredSaTypes = new ArrayList<>();
		unmeteredSaTypes.add("CM_BPUGD");
		unmeteredSaTypes.add("CM_IPUGD");
		unmeteredSaTypes.add("CM_JPUGD");
		unmeteredSaTypes.add("CM_BPUSD");
		unmeteredSaTypes.add("CM_IPUSD");
		unmeteredSaTypes.add("CM_JPUSD");
		unmeteredSaTypes.add("CM_BPUA");
		unmeteredSaTypes.add("CM_IPUA");
		unmeteredSaTypes.add("CM_JPUA");
		unmeteredSaTypes.add("CM_BTUA");
		unmeteredSaTypes.add("CM_ITUA");
		unmeteredSaTypes.add("CM_JTUA");
		unmeteredSaTypes.add("CM_IAGBE");		
		unmeteredSaTypes.add("CM_BAGBE");		
		unmeteredSaTypes.add("CM_JAGBE");
		
		for(CCNBNSCStagingMigration currentRecord: unmigratedRecords) {
			try {
				session.clear();
				session.beginTransaction();
				session.flush();

				//do the preliminary null check
				basicNullCheck(currentRecord);
				
				//Create a new object
				NSCStagingMigration nscStagingMigration = new NSCStagingMigration();
				NSCStagingMigrationStatus nscStagingMigrationStatus = new NSCStagingMigrationStatus();
				
				nscStagingMigration.setPremise_type(currentRecord.getPremise_type());
				
				CcnbNgbTariffMapping tariffMapping = null;
				if(currentRecord.getOld_trf_catg().trim().startsWith("LV1.1")) {
					tariffMapping = getLV1TariffMapping(tariffMappings, currentRecord.getOld_trf_catg().trim());
				}else {					
					if(!(currentRecord.getCcnbPurposeOfInstallation()==null || currentRecord.getPurposeOfInstallationCD()==null))
						tariffMapping = getTariffMapping(tariffMappings, currentRecord.getOld_trf_catg().trim(), currentRecord.getPurposeOfInstallationCD().trim(), currentRecord.getCcnbPurposeOfInstallation().trim());
				}
				
				nscStagingMigration.setPremise_type(currentRecord.getPremise_type());
				
				nscStagingMigration.setIs_employee(currentRecord.isIs_employee());
				nscStagingMigration.setEmployee_no(currentRecord.getEmployee_no());
				
				//Connection date is same as application date
				if(currentRecord.getConnection_date()==null)
					nscStagingMigration.setApplication_date(ccnbDateFormat.parse("10-10-2010"));
				else
					nscStagingMigration.setApplication_date(currentRecord.getConnection_date());

/*				if(currentRecord.getOld_trf_catg().trim().startsWith("LV1") && "N".equals(currentRecord.getMetering_status().trim()) && ("CM_IPUGD".equals(currentRecord.getSaType()) || "CM_BPUGD".equals(currentRecord.getSaType()) || "CM_JPUGD".equals(currentRecord.getSaType()) || "CM_IPUSD".equals(currentRecord.getSaType()) || "CM_BPUSD".equals(currentRecord.getSaType()) || "CM_JPUSD".equals(currentRecord.getSaType())))
					currentRecord.setMetering_status("Y");
				if(Long.parseLong(currentRecord.getMeterRent())>0 || (currentRecord.getMeter_identifier()!=null && !currentRecord.getMeter_identifier().trim().isEmpty()))
					currentRecord.setMetering_status("Y");
				if(currentRecord.getOld_trf_catg().trim().equals("LV1.2-UN"))
					currentRecord.setMetering_status("N");
*/				

				//decide metering status
				if(unmeteredSaTypes.contains(currentRecord.getSaType().trim()))
					currentRecord.setMetering_status("N");
				else
					currentRecord.setMetering_status("Y");
				
				if("Y".equals(currentRecord.getMetering_status().trim()) || (currentRecord.getOld_trf_catg().trim().startsWith("LV2") || currentRecord.getOld_trf_catg().trim().startsWith("LV3") || currentRecord.getOld_trf_catg().trim().startsWith("LV4") || currentRecord.getOld_trf_catg().trim().startsWith("LV5.3"))) {
					nscStagingMigration.setMetering_status("METERED");

					MeterMaster meterMaster = new MeterMaster();
					
					String make="MIG", serialNo="";
					if(currentRecord.getMeter_identifier()!=null && !currentRecord.getMeter_identifier().trim().isEmpty())
						serialNo = currentRecord.getMeter_identifier();						
					else 						
						serialNo = currentRecord.getOld_cons_no();													
					
					meterMaster.setIdentifier(make.concat(serialNo));
					meterMaster.setMake(make);
					meterMaster.setSerialNo(serialNo);
					
					meterMaster.setMf(BigDecimal.ONE);
					
					CCNBMeterMapping meterMapping = null;
					if(!(currentRecord.getMeterCapacity()==null || currentRecord.getMeterCapacity().trim().isEmpty()) && !currentRecord.isHas_ctr())
						meterMapping = getCcnbMeterMapping(currentRecord.getMeterCapacity().trim(), ccnbMeterMappings); 
					if(meterMapping != null) {
						meterMaster.setCode(meterMapping.getMeterCode());
						meterMaster.setCapacity(meterMapping.getMeterCapacity());
						meterMaster.setPhase(meterMapping.getMeterPhase());
						meterMaster.setDescription(meterMapping.getMeterDescription());
					}else {
						String tariffCategory = currentRecord.getOld_trf_catg().trim();
						BigDecimal sanctionedLoad = currentRecord.getSanctioned_load();
						String sanctionedLoadUnit = currentRecord.getSanctioned_load_unit().trim();
						if(tariffCategory.startsWith("LV1") || tariffCategory.startsWith("LV2") || tariffCategory.startsWith("LV3.2")) {
							if(currentRecord.isHas_ctr()) {
								meterMaster.setCapacity("-/5");	
								meterMaster.setPhase("THREE");
								meterMaster.setCode("CTT");
								meterMaster.setDescription("C.T.Three Phase Meter");
							}else {
								if(sanctionedLoad.compareTo(new BigDecimal("3"))<=0 && "KW".equals(sanctionedLoadUnit)) {
									meterMaster.setCapacity("5-30");
									meterMaster.setPhase("SINGLE");
									meterMaster.setCode("WCS");
									meterMaster.setDescription("Whole Current Single Phase Meter");
								}else if(sanctionedLoad.compareTo(new BigDecimal("3"))>0 && sanctionedLoad.compareTo(new BigDecimal("10"))<=0 && "KW".equals(sanctionedLoadUnit)){
									meterMaster.setCapacity("3X10-40");
									meterMaster.setPhase("THREE");
									meterMaster.setCode("WCT");
									meterMaster.setDescription("Whole Current Three Phase Meter");								
								}else if(sanctionedLoad.compareTo(new BigDecimal("10"))>0 && "KW".equals(sanctionedLoadUnit)) {
									meterMaster.setCapacity("3X20-80");
									meterMaster.setPhase("THREE");
									meterMaster.setCode("WCT");
									meterMaster.setDescription("Whole Current Three Phase Meter");								
								}else
									throw new Exception("Couldn't find a suitable meter mapping!");								
							}
														
						}else if(tariffCategory.startsWith("LV3") || tariffCategory.startsWith("LV4") || tariffCategory.startsWith("LV5")) {
							if(currentRecord.isHas_ctr()) {
								meterMaster.setCapacity("-/5");
								meterMaster.setPhase("THREE");
								meterMaster.setCode("CTT");
								meterMaster.setDescription("C.T.Three Phase Meter");
							}else {
								if(sanctionedLoad.compareTo(new BigDecimal("2"))<=0 && "HP".equals(sanctionedLoadUnit)) {
									meterMaster.setCapacity("5-30");
									meterMaster.setPhase("SINGLE");
									meterMaster.setCode("WCS");
									meterMaster.setDescription("Whole Current Single Phase Meter");								
								}else if(sanctionedLoad.compareTo(new BigDecimal("2"))>0 && "HP".equals(sanctionedLoadUnit)) {
									meterMaster.setCapacity("3X20-80");
									meterMaster.setPhase("THREE");
									meterMaster.setCode("WCT");
									meterMaster.setDescription("Whole Current Three Phase Meter");																
								}else
									throw new Exception("Couldn't find a suitable meter mapping!");								
							}
						}
					}
					
					meterMaster.setMeterOwner("DISCOM");
					meterMaster.setPrepaid(false);
					meterMaster.setCreatedBy("CCNB_MIG");
					meterMaster.setCreateOn(new Date());						
				
					nscStagingMigration.setMeter_identifier(meterMaster.getIdentifier());
					nscStagingMigration.setPhase(meterMaster.getPhase());
					
					if(currentRecord.getMeter_installation_date()==null)
						nscStagingMigration.setMeter_installation_date(nscStagingMigration.getApplication_date());
					else
						nscStagingMigration.setMeter_installation_date(currentRecord.getMeter_installation_date());
					
					nscStagingMigration.setMeter_installer_name("MIGRATION");
					nscStagingMigration.setMeter_installer_designation("CCNB_MIG");
					
					//Save meter
					session.save(meterMaster);
				}
				else if("N".equals(currentRecord.getMetering_status().trim()))					
					nscStagingMigration.setMetering_status("UNMETERED");												
				else
					throw new Exception("Invalid metering status!");
				
				if("SC/ST".equals(currentRecord.getCategory().trim()))
					nscStagingMigration.setCategory("ST");
				else if("NOT DISCLOSED".equals(currentRecord.getCategory().trim()))
					nscStagingMigration.setCategory("GENERAL");
				else
					nscStagingMigration.setCategory(currentRecord.getCategory().trim());

				nscStagingMigration.setLocation_code(currentRecord.getLocation_code());
				
				nscStagingMigration.setOld_cons_no(currentRecord.getOld_cons_no());

				zoneQuery = session.createQuery("select shortCode from Zone where code = ?");
				zoneQuery.setParameter(0, currentRecord.getLocation_code().trim());
				String shortCode = (String)zoneQuery.uniqueResult();
				if(shortCode!=null) {
					String oldGroupNo = currentRecord.getOld_gr_no().trim();
					String groupNo = "";
					for(int x=0; x<oldGroupNo.length(); x++) {
						if(Character.isDigit(oldGroupNo.charAt(x)))
							groupNo += oldGroupNo.charAt(x);
					}
					Integer newGroupNo = Integer.parseInt(groupNo);
					groupNo = newGroupNo.toString();
					nscStagingMigration.setGroup_no(shortCode.trim().concat(groupNo));
				}
				
				nscStagingMigration.setOld_rd_no(currentRecord.getOld_rd_no());
				
				if(currentRecord.getOld_rd_no().trim().equals("0"))
					nscStagingMigration.setReading_diary_no("1");
				else				
					nscStagingMigration.setReading_diary_no(String.valueOf(Integer.parseInt(currentRecord.getOld_rd_no())));
				
				nscStagingMigration.setAffiliated(currentRecord.isAffiliated());
				
				nscStagingMigration.setAffiliatedConsumer(currentRecord.isAffiliatedConsumer());
				
				nscStagingMigration.setOld_gr_no(currentRecord.getOld_gr_no());
				
				nscStagingMigration.setConsumer_name(currentRecord.getConsumer_name());
				
				nscStagingMigration.setAddress_1(currentRecord.getAddress_1());
				
				nscStagingMigration.setAddress_2(currentRecord.getAddress_2());
				
				nscStagingMigration.setAddress_3(currentRecord.getAddress_3());
				
				nscStagingMigration.setOld_trf_catg(currentRecord.getOld_trf_catg());
				
				nscStagingMigration.setOld_rev_catg(currentRecord.getOld_rev_catg());
				
				nscStagingMigration.setSanctioned_load(currentRecord.getSanctioned_load());
				
				nscStagingMigration.setSanctioned_load_unit(currentRecord.getSanctioned_load_unit());
				
				nscStagingMigration.setSd_enhance_cd(currentRecord.getSd_enhance_cd());

				if("I".equals(currentRecord.getDishnrd_chq_flg().trim()))
					nscStagingMigration.setDishnrd_chq_flg("N");
				else if("A".equals(currentRecord.getDishnrd_chq_flg().trim()))
					nscStagingMigration.setDishnrd_chq_flg("Y");

				nscStagingMigration.setContract_demand_unit(currentRecord.getContract_demand_unit());

				if(currentRecord.getOld_trf_catg().trim().startsWith("LV4") && "HP".equals(currentRecord.getContract_demand_unit().trim())) {
					BigDecimal contractDemand = currentRecord.getContract_demand().multiply(new BigDecimal("0.746"));
					contractDemand = contractDemand.setScale(3, RoundingMode.HALF_UP);
					
					nscStagingMigration.setContract_demand(contractDemand);
					nscStagingMigration.setContract_demand_unit("KW");
				}else
					nscStagingMigration.setContract_demand(currentRecord.getContract_demand());
				
				if(currentRecord.getOld_trf_catg().startsWith("LV2")) {
					if(currentRecord.getSanctioned_load().compareTo(new BigDecimal("10"))>0 && currentRecord.getSanctioned_load().compareTo(nscStagingMigration.getContract_demand())<0)
						throw new Exception("Sanctioned load can't be less than the contract demand!");
					if(currentRecord.getSanctioned_load().compareTo(new BigDecimal("10"))>0 && currentRecord.getSanctioned_load().compareTo(BigDecimal.ZERO)==0)
						throw new Exception("Contract demand can't be zero for LV2!");
				}else if(currentRecord.getOld_trf_catg().startsWith("LV4")) {
					BigDecimal sanctionedLoad = currentRecord.getSanctioned_load().multiply(new BigDecimal("0.746"));
					sanctionedLoad = sanctionedLoad.setScale(3, RoundingMode.HALF_UP);
					if(sanctionedLoad.compareTo(nscStagingMigration.getContract_demand())<0)
						throw new Exception("Sanctioned load can't be less than the contract demand!");
					if(sanctionedLoad.compareTo(BigDecimal.ZERO)==0)
						throw new Exception("Contract demand can't be zero for LV4!");
				}
				
				nscStagingMigration.setTotal_outstanding(currentRecord.getTotal_outstanding());
				
				nscStagingMigration.setPrev_arrear(currentRecord.getPrev_arrear());
				
				if(Double.parseDouble(currentRecord.getPend_surcharge())<0)
					nscStagingMigration.setPend_surcharge("0");
				else
					nscStagingMigration.setPend_surcharge(currentRecord.getPend_surcharge());
				
				nscStagingMigration.setCurr_surcharge(currentRecord.getCurr_surcharge());
				
				nscStagingMigration.setColl_surcharge(currentRecord.getColl_surcharge());
				
				nscStagingMigration.setPfl_bill_amount(currentRecord.getPfl_bill_amount());
				
				nscStagingMigration.setMf("1");
				
				nscStagingMigration.setNrev_catg(currentRecord.getNrev_catg());
				
				String primaryMobileNo = currentRecord.getPrimary_mobile_no();
				if(primaryMobileNo!=null) {
					if(primaryMobileNo.length()==10) {
						if(!(primaryMobileNo.startsWith("0") || primaryMobileNo.startsWith("1") || primaryMobileNo.startsWith("2") || primaryMobileNo.startsWith("3") || primaryMobileNo.startsWith("4") || primaryMobileNo.startsWith("5") || primaryMobileNo.equals("9999999999") || primaryMobileNo.equals("8888888888") || primaryMobileNo.equals("7777777777") || primaryMobileNo.equals("6666666666"))) 
							nscStagingMigration.setPrimary_mobile_no(currentRecord.getPrimary_mobile_no());													
					}
					else if(primaryMobileNo.length()==12 || primaryMobileNo.length()==13) {
						if(primaryMobileNo.startsWith("+91")) {
							primaryMobileNo = primaryMobileNo.substring(3);
							if(!(primaryMobileNo.startsWith("0") || primaryMobileNo.startsWith("1") || primaryMobileNo.startsWith("2") || primaryMobileNo.startsWith("3") || primaryMobileNo.startsWith("4") || primaryMobileNo.startsWith("5") || primaryMobileNo.equals("9999999999") || primaryMobileNo.equals("8888888888") || primaryMobileNo.equals("7777777777") || primaryMobileNo.equals("6666666666"))) 
								nscStagingMigration.setPrimary_mobile_no(primaryMobileNo);							
						}
						else if(primaryMobileNo.startsWith("91")) {
							primaryMobileNo = primaryMobileNo.substring(2);
							if(!(primaryMobileNo.startsWith("0") || primaryMobileNo.startsWith("1") || primaryMobileNo.startsWith("2") || primaryMobileNo.startsWith("3") || primaryMobileNo.startsWith("4") || primaryMobileNo.startsWith("5") || primaryMobileNo.equals("9999999999") || primaryMobileNo.equals("8888888888") || primaryMobileNo.equals("7777777777") || primaryMobileNo.equals("6666666666"))) 
								nscStagingMigration.setPrimary_mobile_no(primaryMobileNo);							
						}
					}
				}
				
				nscStagingMigration.setIs_bpl(currentRecord.isIs_bpl());
				if(currentRecord.getOld_trf_catg().trim().startsWith("LV2") || currentRecord.getOld_trf_catg().trim().startsWith("LV3") || currentRecord.getOld_trf_catg().trim().startsWith("LV4"))
					nscStagingMigration.setIs_bpl(false);
					
				if(currentRecord.isIs_bpl()) {
					if(currentRecord.getBpl_no()==null || currentRecord.getBpl_no().trim().isEmpty())
						nscStagingMigration.setBpl_no("MIG".concat(currentRecord.getOld_cons_no()));
					else
						nscStagingMigration.setBpl_no(currentRecord.getBpl_no());					
				}
				
				if(currentRecord.getAadhaar_no()!=null && currentRecord.getAadhaar_no().length()==12)
					nscStagingMigration.setAadhaar_no(currentRecord.getAadhaar_no());
				
				nscStagingMigration.setConsumer_name_h(currentRecord.getConsumer_name_h());
				
				nscStagingMigration.setAddress_1_h(currentRecord.getAddress_1_h());
				
				nscStagingMigration.setAddress_2_h(currentRecord.getAddress_2_h());
				
				nscStagingMigration.setAddress_3_h(currentRecord.getAddress_3_h());
				
				nscStagingMigration.setIs_beneficiary(currentRecord.isIs_beneficiary());
				
				//Tariff Category
				nscStagingMigration.setTariff_category(currentRecord.getOld_trf_catg().substring(0, 3));

				if(currentRecord.getOld_trf_catg().contains("T") || currentRecord.getOld_trf_catg().trim().equals("LV1.2N"))
					nscStagingMigration.setConnection_type("TEMPORARY");
				else					
					nscStagingMigration.setConnection_type(currentRecord.getConnection_type());
				
				nscStagingMigration.setIs_seasonal(currentRecord.isIs_seasonal());
				
				//Check phase based on the load
				if(currentRecord.getMetering_status().equals("N")) {
					if("HP".equals(currentRecord.getSanctioned_load_unit())) {
						if(currentRecord.getSanctioned_load().compareTo(new BigDecimal("2.5"))>0)
							nscStagingMigration.setPhase("THREE");
						else
							nscStagingMigration.setPhase("SINGLE");
					}else if("KW".equals(currentRecord.getSanctioned_load_unit())) {
						if(currentRecord.getSanctioned_load().compareTo(new BigDecimal("3"))>0)
							nscStagingMigration.setPhase("THREE");
						else
							nscStagingMigration.setPhase("SINGLE");					
					}
					
				}
				nscStagingMigration.setIs_government(currentRecord.isIs_government());
				if(nscStagingMigration.getMetering_status().equals("UNMETERED"))
					nscStagingMigration.setIs_government(false);
				
				nscStagingMigration.setPlot_size(currentRecord.getPlot_size());
				
				nscStagingMigration.setPlot_size_unit(currentRecord.getPlot_size_unit());
				
				nscStagingMigration.setIs_xray(currentRecord.isIs_xray());
				
				nscStagingMigration.setNo_of_dental_xray_machine(currentRecord.getNo_of_dental_xray_machine());
				
				nscStagingMigration.setXray_load(currentRecord.getXray_load());
				
				nscStagingMigration.setNo_of_single_phase_xray_machine(currentRecord.getNo_of_single_phase_xray_machine());
				
				nscStagingMigration.setNo_of_three_phase_xray_machine(currentRecord.getNo_of_three_phase_xray_machine());
				
				if(currentRecord.getOld_trf_catg().trim().startsWith("LV1"))
					nscStagingMigration.setIs_welding_transformer_surcharge(false);
				else					
					nscStagingMigration.setIs_welding_transformer_surcharge(currentRecord.isIs_welding_transformer_surcharge());
				
				if(currentRecord.getOld_trf_catg().trim().startsWith("LV1"))
					nscStagingMigration.setIs_capacitor_surcharge(false);
				else					
				nscStagingMigration.setIs_capacitor_surcharge(currentRecord.isIs_capacitor_surcharge());
				
				nscStagingMigration.setIs_demandside(currentRecord.isIs_demandside());
				
				nscStagingMigration.setHas_ctr(currentRecord.isHas_ctr());
				
				if(currentRecord.isHas_ctr()) {
					//Create a new CTR
					String meterCapacity, ctRatio;
					CTRMaster ctrMaster = new CTRMaster();
					
					if(currentRecord.getCtr_overall_mf().compareTo(new BigDecimal("2"))==0)
					{
						meterCapacity = "100/5";
						ctRatio = "200/5";
					}
					else if(currentRecord.getCtr_overall_mf().compareTo(new BigDecimal("1.5"))==0)
					{
						meterCapacity = "100/5";
						ctRatio = "150/5";					
					}
					else if(currentRecord.getCtr_overall_mf().compareTo(new BigDecimal("3"))==0)
					{
						meterCapacity = "100/5";
						ctRatio = "300/5";					
					}
					else if(currentRecord.getCtr_overall_mf().compareTo(new BigDecimal("4"))==0)
					{
						meterCapacity = "100/5";
						ctRatio = "400/5";					
					}
					else if(currentRecord.getCtr_overall_mf().compareTo(new BigDecimal("5"))==0)
					{
						meterCapacity = "100/5";
						ctRatio = "500/5";					
					}
					else if(currentRecord.getCtr_overall_mf().compareTo(new BigDecimal("10"))==0)
					{
						meterCapacity = "-/5";
						ctRatio = "50/5";					
					}
					else if(currentRecord.getCtr_overall_mf().compareTo(new BigDecimal("15"))==0)
					{
						meterCapacity = "-/5";
						ctRatio = "75/5";					
					}
					else if(currentRecord.getCtr_overall_mf().compareTo(new BigDecimal("20"))==0)
					{
						meterCapacity = "-/5";
						ctRatio = "100/5";					
					}
					else if(currentRecord.getCtr_overall_mf().compareTo(new BigDecimal("30"))==0)
					{
						meterCapacity = "-/5";
						ctRatio = "150/5";					
					}
					else if(currentRecord.getCtr_overall_mf().compareTo(new BigDecimal("40"))==0)
					{
						meterCapacity = "-/5";
						ctRatio = "200/5";					
					}
					else if(currentRecord.getCtr_overall_mf().compareTo(new BigDecimal("60"))==0)
					{
						meterCapacity = "-/5";
						ctRatio = "300/5";					
					}
					else if(currentRecord.getCtr_overall_mf().compareTo(new BigDecimal("80"))==0)
					{
						meterCapacity = "-/5";
						ctRatio = "400/5";					
					}
					else
						throw new Exception("Invalid MF !!");
					
					ctrMaster.setMake("MIG");
					ctrMaster.setCtRatio(ctRatio);
					ctrMaster.setCreatedBy("CCNB_MIG");
					ctrMaster.setCreatedOn(new Date());
					ctrMaster.setSerialNo(currentRecord.getOld_cons_no());
					ctrMaster.setIdentifier("MIGCTR".concat(currentRecord.getOld_cons_no()));					
					
					if(currentRecord.getCtr_overall_mf().compareTo(BigDecimal.ZERO)==0)
						throw new Exception("CTR overall MF can't be 0!");
										
					nscStagingMigration.setCtr_identifier(ctrMaster.getIdentifier());
					nscStagingMigration.setCtr_overall_mf(currentRecord.getCtr_overall_mf());
				
					//Save CTR
					session.save(ctrMaster);
				}
				
				nscStagingMigration.setHas_modem(currentRecord.isHas_modem());
				
				//Forcefully set the positive security deposit to zero and convert the negative security deposit to positive
				if(currentRecord.getSecurity_deposit_amount().compareTo(BigDecimal.ZERO)>0)
					nscStagingMigration.setSecurity_deposit_amount(BigDecimal.ZERO);
				else
					nscStagingMigration.setSecurity_deposit_amount(currentRecord.getSecurity_deposit_amount().abs());

				nscStagingMigration.setPortal_name("CCNB MIGRATION");
				
				nscStagingMigration.setPortal_reference_no("CCNB_MIG 1");
				
				nscStagingMigration.setCreated_on(new Date());
				
				if("LEGAL".equals(currentRecord.getArea_status().trim()))
					nscStagingMigration.setArea_status("AUTHORISED");
				else
					nscStagingMigration.setArea_status("UNAUTHORISED");
				
				if("C".equals(currentRecord.getStatus().trim()))
					nscStagingMigration.setStatus("ACTIVE");
				else if("D".equals(currentRecord.getStatus().trim()))
					nscStagingMigration.setStatus("INACTIVE");
				else
					throw new Exception("Invalid status!");
				

				//Check if we have a tariff mapping available or not
				if(tariffMapping==null) {
					if(currentRecord.getOld_trf_catg().trim().equals("LV1.2-UN")) {
						//For LV1.2.TM
						decideTariffForLV1UM(nscStagingMigration, currentRecord);
					}
					else if(currentRecord.getOld_trf_catg().trim().startsWith("LV3")) {						
						//For LV3
						decideTariffForLV3(nscStagingMigration, currentRecord);
						
					}else if(currentRecord.getOld_trf_catg().trim().startsWith("LV5")) {						
						//For LV5
						decideTariffForLV5(nscStagingMigration, currentRecord);
						
					}else
						throw new Exception("Couldn't find a suitable tariff category");
				}else {
					decideTariffFromTariffMapping(nscStagingMigration, currentRecord, tariffMapping);
				}
				
				//save nsc staging migration object
				session.save(nscStagingMigration);
				
				//Create staging status object
				nscStagingMigrationStatus.setStatus("PENDING");
				nscStagingMigrationStatus.setCreated_on(new Date());
				nscStagingMigrationStatus.setLast_updated_on(new Date());
				nscStagingMigrationStatus.setLocation_code(currentRecord.getLocation_code());
				nscStagingMigrationStatus.setOld_cons_no(currentRecord.getOld_cons_no());
				nscStagingMigrationStatus.setNsc_staging_id(nscStagingMigration.getId());

				//save nsc staging migration status object
				session.save(nscStagingMigrationStatus);
				
				//commit
				session.getTransaction().commit();
				
				//Update the migrated flag
				session.beginTransaction();
				
				migrationStatus = session.createQuery("update CCNBNSCStagingMigration set migrated=true where id=?");
				migrationStatus.setParameter(0, currentRecord.getId());
				migrationStatus.executeUpdate();
				session.getTransaction().commit();
				
				++recordCount;
				System.out.println();
			}catch(Exception e) {
				++exceptionCount;				
				writer.println();
				writer.println("***********EXCEPTION NUMBER " + exceptionCount + "***********" + "Occured on: " + new Date());
				writer.println("***********CONSUMER NUMBER: " + currentRecord.getOld_cons_no() + "*** OLD TARIFF CATEGORY: " + currentRecord.getOld_trf_catg() + "*** PREMISE TYPE: " + currentRecord.getPremise_type());
				writer.println("Root cause : ");
				e.printStackTrace(writer);
				e.printStackTrace();
				session.getTransaction().rollback();
			}
		}
		
		long endTime = System.currentTimeMillis();
		long totTime = endTime - startTime;
		long seconds = (endTime - startTime)/1000;
		long minutes = seconds/60;
		seconds -= minutes*60;
		
		System.out.println("MIGRATION FROM CCNB_NSC_STAGING_MIGRATION TO NSC_STAGING_MIGRATION SUCCESSFULLY DONE !!");
		System.out.println(unmigratedRecords.size() + " ROWS WERE RETRIEVED");
		System.out.println(recordCount + " ROWS SUCCESSFULLY MIGRATED !!");		
		System.out.println(exceptionCount + " EXCEPTIONS CAUGHT !! PLEASE REFER EXCEPTION LOG FOR MORE DETAILS!!");		
		System.out.println("Time Elapsed: " + minutes + " Minutes " + seconds + " Seconds");				
		
		session.close();
		sessionFactory.close();
		writer.close();		
	}
	
	private static CcnbNgbTariffMapping getTariffMapping(List<CcnbNgbTariffMapping> tariffMappings, String ccnbTariff, String ccnbCharTypeCd, String ccnbCharVal) {
		CcnbNgbTariffMapping reqMapping = null;
		for(CcnbNgbTariffMapping tariffMapping: tariffMappings) {
			if(tariffMapping.getCcnbTariff().trim().equals(ccnbTariff) && tariffMapping.getCcnbCharTypeCd().trim().equals(ccnbCharTypeCd) && tariffMapping.getCcnbCharVal().trim().equals(ccnbCharVal))
				reqMapping = tariffMapping;			
		}
		return reqMapping;
	}

	private static void basicNullCheck(CCNBNSCStagingMigration currentRecord) throws Exception {
		ArrayList<String> exceptionList = new ArrayList<>();

		if(currentRecord.getConsumer_name()==null || currentRecord.getConsumer_name().trim().isEmpty()) {
			exceptionList.add("Consumer Name was found to be blank");
		}
		if((currentRecord.getCategory()==null || 
				currentRecord.getCategory().trim().isEmpty()) && !currentRecord.getOld_trf_catg().trim().startsWith("LV3")) {
			exceptionList.add("Category was found to be blank");
		}
		if((currentRecord.getConnection_type()==null ||
				currentRecord.getConnection_type().trim().isEmpty())) {
			exceptionList.add("Connection Type was found to be blank");
		}
		if(currentRecord.getMetering_status()==null || 
				currentRecord.getMetering_status().trim().isEmpty()){
			exceptionList.add("Metering Status was found to be blank");
		}
		if(currentRecord.getPremise_type()==null ||
				currentRecord.getPremise_type().trim().isEmpty()){
			exceptionList.add("Premise Type was found to be blank");
		}
		if(currentRecord.getSanctioned_load()==null ||
				currentRecord.getSanctioned_load().compareTo(BigDecimal.ZERO)==0){
			exceptionList.add("Sanctioned Load was found to be blank");
		}
		if(currentRecord.getSanctioned_load_unit()==null || 						
				currentRecord.getSanctioned_load_unit().trim().isEmpty()) {
			exceptionList.add("Sanctioned Load Unit was found to be blank");
		}
		if((currentRecord.getCcnbPurposeOfInstallation()==null ||
				currentRecord.getCcnbPurposeOfInstallation().trim().isEmpty()) && !currentRecord.getOld_trf_catg().startsWith("LV3") && !currentRecord.getOld_trf_catg().trim().startsWith("LV1.1")) {
			exceptionList.add("CCNB Purpose of Installation was found to be blank");
		}
		if(currentRecord.getLocation_code()==null || 
				currentRecord.getLocation_code().trim().isEmpty()){
			exceptionList.add("Location Code was found to be blank");
		}
		if(currentRecord.getOld_cons_no()==null ||  
				currentRecord.getOld_cons_no().trim().isEmpty()){
			exceptionList.add("Old Cons No was found to be blank");
		}
		if(currentRecord.getOld_gr_no()==null || 
				currentRecord.getOld_gr_no().trim().isEmpty()){
			exceptionList.add("Old Grp No was found to be blank");
		}
		if(currentRecord.getOld_rd_no()==null ||
				currentRecord.getOld_rd_no().trim().isEmpty()){
			exceptionList.add("Old RD No was found to be blank");
		}
		if(currentRecord.getOld_trf_catg()==null || 
				currentRecord.getOld_trf_catg().trim().isEmpty()){
			exceptionList.add("Old Tariff Category was found to be blank");
		}
		if((currentRecord.getPurposeOfInstallationCD()==null ||
				currentRecord.getPurposeOfInstallationCD().trim().isEmpty()) && !currentRecord.getOld_trf_catg().startsWith("LV3") && !currentRecord.getOld_trf_catg().trim().startsWith("LV1.1")){
			exceptionList.add("Purpose of Installation Code was found to be blank");
		}
		if(currentRecord.getStatus()==null ||
				currentRecord.getStatus().trim().isEmpty()){
			exceptionList.add("Status was found to be blank");
		}
		if(currentRecord.isIs_employee() && currentRecord.getEmployee_no().trim().isEmpty()){
			exceptionList.add("Emplyee No was found to be blank");
		}
		if(currentRecord.isIs_seasonal() && (currentRecord.getSeason_start_date()==null || currentRecord.getSeason_end_date()==null || currentRecord.getSeason_start_bill_month().trim().isEmpty() || currentRecord.getSeason_end_bill_month().trim().isEmpty())){
			exceptionList.add("Seasonal Flag was true but related information was found to be blank");
		}
		
		if(!exceptionList.isEmpty())
			throw new Exception("List of Null check errors -> " + exceptionList);
	}
	
	private static int getMeterMakeIndex(String identifier) {
		int index=-1;
		for(int i=0;i<identifier.length();i++) {
			if(Character.isAlphabetic(identifier.charAt(i)))
				++index;
			else
				break;
		}
		return index;
	}
	
	private static void decideTariffForLV1UM(NSCStagingMigration nscStagingMigration, CCNBNSCStagingMigration currentRecord) throws Exception{
		if(currentRecord.getSanctioned_load_unit().equals("KW") && currentRecord.getPremise_type().trim().equals("RURAL")) {

			nscStagingMigration.setTariff_category("LV1");
			nscStagingMigration.setPurpose_of_installation_id(140);
			nscStagingMigration.setTariff_code("LV1.UM");
			nscStagingMigration.setPurpose_of_installation("Domestic light and fan Un-Metered");
			
			BigDecimal sanctionedLoad = nscStagingMigration.getSanctioned_load().setScale(1, RoundingMode.HALF_UP);
			if(sanctionedLoad.compareTo(new BigDecimal("0.2"))<=0) {
				nscStagingMigration.setSub_category_code(115);
				
			}else if(sanctionedLoad.compareTo(new BigDecimal("0.3"))<=0) {
				nscStagingMigration.setSub_category_code(116);
				
			}else if(sanctionedLoad.compareTo(new BigDecimal("0.5"))<=0) {
				nscStagingMigration.setSub_category_code(117);
				
			}else
				throw new Exception("Load can't be greater than 0.5 for LV1.2-UN tariff category");
			
		}else
			throw new Exception("Load unit should be 'KW' and premise type should be 'RURAL' for LV1.2-UN tariff category");		
	}
	
	private static void decideTariffForLV3(NSCStagingMigration nscStagingMigration, CCNBNSCStagingMigration currentRecord) throws Exception{
		nscStagingMigration.setTariff_category("LV3");
		
		if(currentRecord.getOld_trf_catg().trim().startsWith("LV3.1") && currentRecord.getSanctioned_load_unit().equals("KW")) {
			currentRecord.setSanctioned_load(currentRecord.getSanctioned_load().divide(new BigDecimal("0.746"), 2, RoundingMode.HALF_UP));
			currentRecord.setSanctioned_load_unit("HP");
			nscStagingMigration.setSanctioned_load(currentRecord.getSanctioned_load());
			nscStagingMigration.setSanctioned_load_unit(currentRecord.getSanctioned_load_unit());
		}
		
		if(currentRecord.getOld_trf_catg().trim().startsWith("LV3.2") && currentRecord.getSanctioned_load_unit().equals("HP")) {
			currentRecord.setSanctioned_load(currentRecord.getSanctioned_load().multiply(new BigDecimal("0.746")).setScale(2, RoundingMode.HALF_UP));
			currentRecord.setSanctioned_load_unit("KW");
			nscStagingMigration.setSanctioned_load(currentRecord.getSanctioned_load());
			nscStagingMigration.setSanctioned_load_unit(currentRecord.getSanctioned_load_unit());
		}
		
		if(currentRecord.getOld_trf_catg().trim().equals("LV3.1.A") || currentRecord.getOld_trf_catg().trim().equals("LV3.1A-B") || currentRecord.getOld_trf_catg().trim().equals("LV3.1A-I")) {
			
			nscStagingMigration.setPurpose_of_installation_id(52);
			nscStagingMigration.setTariff_code("LV3.1");
			nscStagingMigration.setPurpose_of_installation("Public Utility Water Supply Schemes");
			
			if("URBAN".equals(currentRecord.getPremise_type()))
				nscStagingMigration.setSub_category_code(301);
			else
				throw new Exception("Couldn't find a suitable subcategory code!!");			

		}else if(currentRecord.getOld_trf_catg().trim().equals("LV3.1A.T") || currentRecord.getOld_trf_catg().trim().equals("LV3.1B.T")) {
			
			nscStagingMigration.setPurpose_of_installation_id(63);
			nscStagingMigration.setTariff_code("LV3.1T");				
			nscStagingMigration.setPurpose_of_installation("Temporary Public Water Works");
			
			if("URBAN".equals(currentRecord.getPremise_type()))
				nscStagingMigration.setSub_category_code(304);
			else
				throw new Exception("Couldn't find a suitable subcategory code!!");			
		
		}else if(currentRecord.getOld_trf_catg().trim().equals("LV3.1.B") || currentRecord.getOld_trf_catg().trim().equals("LV3.1B-B") || currentRecord.getOld_trf_catg().trim().equals("LV3.1B-I")) {
			
			nscStagingMigration.setPurpose_of_installation_id(52);
			nscStagingMigration.setTariff_code("LV3.1");
			nscStagingMigration.setPurpose_of_installation("Public Utility Water Supply Schemes");
			
			if("URBAN".equals(currentRecord.getPremise_type()))
				nscStagingMigration.setSub_category_code(302);
			else
				throw new Exception("Couldn't find a suitable subcategory code!!");			
		
		}else if(currentRecord.getOld_trf_catg().trim().equals("LV3.1.C") || currentRecord.getOld_trf_catg().trim().equals("LV3.1C-B") || currentRecord.getOld_trf_catg().trim().equals("LV3.1C-I")) {
			
			nscStagingMigration.setPurpose_of_installation_id(52);
			nscStagingMigration.setTariff_code("LV3.1");
			nscStagingMigration.setPurpose_of_installation("Public Utility Water Supply Schemes");
			
			if("RURAL".equals(currentRecord.getPremise_type()))
				nscStagingMigration.setSub_category_code(303);
			else
				throw new Exception("Couldn't find a suitable subcategory code!!");			
			
		}else if(currentRecord.getOld_trf_catg().trim().equals("LV3.1C.T")) {
			
			nscStagingMigration.setPurpose_of_installation_id(63);
			nscStagingMigration.setTariff_code("LV3.1T");				
			nscStagingMigration.setPurpose_of_installation("Temporary Public Water Works");
			
			if("RURAL".equals(currentRecord.getPremise_type()))
				nscStagingMigration.setSub_category_code(305);
			else
				throw new Exception("Couldn't find a suitable subcategory code!!");			
		
		}else if(currentRecord.getOld_trf_catg().trim().equals("LV3.2.A") || currentRecord.getOld_trf_catg().trim().equals("LV3.2A-B") || currentRecord.getOld_trf_catg().trim().equals("LV3.2A-I")) {
			
			nscStagingMigration.setPurpose_of_installation_id(57);
			nscStagingMigration.setTariff_code("LV3.2");							
			nscStagingMigration.setPurpose_of_installation("Public street Lights");
			
			if("URBAN".equals(currentRecord.getPremise_type()))
				nscStagingMigration.setSub_category_code(306);
			else
				throw new Exception("Couldn't find a suitable subcategory code!!");			
		
		}else if(currentRecord.getOld_trf_catg().trim().equals("LV3.2.B") || currentRecord.getOld_trf_catg().trim().equals("LV3.2B-B") || currentRecord.getOld_trf_catg().trim().equals("LV3.2B-I")) {
			
			nscStagingMigration.setPurpose_of_installation_id(57);
			nscStagingMigration.setTariff_code("LV3.2");							
			nscStagingMigration.setPurpose_of_installation("Public street Lights");
			
			if("URBAN".equals(currentRecord.getPremise_type()))
				nscStagingMigration.setSub_category_code(307);
			else
				throw new Exception("Couldn't find a suitable subcategory code!!");			
		
		}else if(currentRecord.getOld_trf_catg().trim().equals("LV3.2.C") || currentRecord.getOld_trf_catg().trim().equals("LV3.2C-B") || currentRecord.getOld_trf_catg().trim().equals("LV3.2C-I")) {
			
			nscStagingMigration.setPurpose_of_installation_id(57);
			nscStagingMigration.setTariff_code("LV3.2");							
			nscStagingMigration.setPurpose_of_installation("Public street Lights");
			
			if("RURAL".equals(currentRecord.getPremise_type()))
				nscStagingMigration.setSub_category_code(308);
			else
				throw new Exception("Couldn't find a suitable subcategory code!!");			
		
		}else
			throw new Exception("Couldn't find a suitable tariff for the consumer!");
	}
	
	private static void decideTariffForLV5(NSCStagingMigration nscStagingMigration, CCNBNSCStagingMigration currentRecord) throws Exception{
		String ccnbTariffCategory = currentRecord.getOld_trf_catg().trim();
		String ccnbPurposeOfInstallation =  "";
		if(currentRecord.getCcnbPurposeOfInstallation()!=null)
			ccnbPurposeOfInstallation = currentRecord.getCcnbPurposeOfInstallation().trim();
		String ccnbPurposeOfInstallationCd = "";
		if(currentRecord.getPurposeOfInstallationCD()!=null)
			ccnbPurposeOfInstallationCd = currentRecord.getPurposeOfInstallationCD().trim();
		String phase = nscStagingMigration.getPhase();
		String premiseType = currentRecord.getPremise_type().trim();
		nscStagingMigration.setTariff_category("LV5");
		
		if(currentRecord.getSanctioned_load_unit().equals("KW")) {
			currentRecord.setSanctioned_load(currentRecord.getSanctioned_load().divide(new BigDecimal("0.746"), 2, RoundingMode.HALF_UP));
			currentRecord.setSanctioned_load_unit("HP");
			nscStagingMigration.setSanctioned_load(currentRecord.getSanctioned_load());
			nscStagingMigration.setSanctioned_load_unit(currentRecord.getSanctioned_load_unit());
		}		
		
		if((ccnbTariffCategory.equals("LV5.1B") || ccnbTariffCategory.equals("LV5.1B3") || ccnbTariffCategory.equals("LV5.2.B") || ccnbTariffCategory.equals("LV5.2.B3")) && ccnbPurposeOfInstallationCd.equals("CM_USAGO") && ccnbPurposeOfInstallation.equals("TEMP_AG_UM")) {
			nscStagingMigration.setPurpose_of_installation_id(141);
			nscStagingMigration.setPurpose_of_installation("Flat Rate Temporary");
			nscStagingMigration.setTariff_code("LV5.1BT.UM");

			if(phase.equals("THREE")) {
				if("URBAN".equals(premiseType))
					nscStagingMigration.setSub_category_code(517);
				else if("RURAL".equals(premiseType))
					nscStagingMigration.setSub_category_code(518);
				else
					throw new Exception("Invalid premise type!");
			}else if(phase.equals("SINGLE")) {
				if("URBAN".equals(premiseType))
					nscStagingMigration.setSub_category_code(519);
				else if("RURAL".equals(premiseType))
					nscStagingMigration.setSub_category_code(520);				
				else
					throw new Exception("Invalid premise type!");
			}else
				throw new Exception("Invalid phase!");
			
		}else if((ccnbTariffCategory.equals("LV5.1B") || ccnbTariffCategory.equals("LV5.1B3") || ccnbTariffCategory.equals("LV5.2.B") || ccnbTariffCategory.equals("LV5.2.B") || ccnbTariffCategory.equals("LV5.2.B3") || ccnbTariffCategory.equals("LV5.3.2") || ccnbTariffCategory.equals("LV5.3.3")) && ccnbPurposeOfInstallationCd.equals("CM_USTLT") && ccnbPurposeOfInstallation.equals("TEMP_AG_UM")) {
			nscStagingMigration.setPurpose_of_installation_id(141);
			nscStagingMigration.setPurpose_of_installation("Flat Rate Temporary");
			nscStagingMigration.setTariff_code("LV5.1BT.UM");

			if(phase.equals("THREE")) {
				if("URBAN".equals(premiseType))
					nscStagingMigration.setSub_category_code(517);
				else if("RURAL".equals(premiseType))
					nscStagingMigration.setSub_category_code(518);
				else
					throw new Exception("Invalid premise type!");
			}else if(phase.equals("SINGLE")) {
				if("URBAN".equals(premiseType))
					nscStagingMigration.setSub_category_code(519);
				else if("RURAL".equals(premiseType))
					nscStagingMigration.setSub_category_code(520);				
				else
					throw new Exception("Invalid premise type!");
			}else
				throw new Exception("Invalid phase!");
			
		}else if((ccnbTariffCategory.equals("LV5.2.C") || ccnbTariffCategory.equals("LV5.2A-B") || ccnbTariffCategory.equals("LV5.2A-I") || ccnbTariffCategory.equals("LV5.3") || ccnbTariffCategory.equals("LV5.3.1")) && ccnbPurposeOfInstallationCd.equals("CM_USAGO")) {

			if(ccnbPurposeOfInstallation.equals("AQUACULTURE")) {
				nscStagingMigration.setPurpose_of_installation_id(127);
				nscStagingMigration.setPurpose_of_installation("Aquaculture");

				if(nscStagingMigration.getSanctioned_load().compareTo(new BigDecimal("25"))<=0 && "HP".equals(nscStagingMigration.getSanctioned_load_unit())) {
					if("URBAN".equals(premiseType))
						nscStagingMigration.setSub_category_code(513);
					else if("RURAL".equals(premiseType))
						nscStagingMigration.setSub_category_code(514);				
					else
						throw new Exception("Invalid premise type!");					
				}else if(nscStagingMigration.getSanctioned_load().compareTo(new BigDecimal("25"))>0 && "HP".equals(nscStagingMigration.getSanctioned_load_unit()) && (ccnbTariffCategory.equals("LV5.3") || ccnbTariffCategory.equals("LV5.3.1"))) {					
					nscStagingMigration.setPurpose_of_installation_id(134);
					nscStagingMigration.setPurpose_of_installation("Demand based Aquaculture");
					if("URBAN".equals(premiseType))
						nscStagingMigration.setSub_category_code(515);
					else if("RURAL".equals(premiseType))
						nscStagingMigration.setSub_category_code(516);				
					else
						throw new Exception("Invalid premise type!");					
				}else
					throw new Exception("Couldn't find suitable subcategory");

			}else if(ccnbPurposeOfInstallation.equals("CTLE_BRDG_FARM")) {
				nscStagingMigration.setPurpose_of_installation_id(131);
				nscStagingMigration.setPurpose_of_installation("Cattle breeding farms");

				if(nscStagingMigration.getSanctioned_load().compareTo(new BigDecimal("25"))<=0 && "HP".equals(nscStagingMigration.getSanctioned_load_unit())) {
					if("URBAN".equals(premiseType))
						nscStagingMigration.setSub_category_code(513);
					else if("RURAL".equals(premiseType))
						nscStagingMigration.setSub_category_code(514);				
					else
						throw new Exception("Invalid premise type!");					
				}else if(nscStagingMigration.getSanctioned_load().compareTo(new BigDecimal("25"))>0 && "HP".equals(nscStagingMigration.getSanctioned_load_unit()) && (ccnbTariffCategory.equals("LV5.3") || ccnbTariffCategory.equals("LV5.3.1"))) {					
					nscStagingMigration.setPurpose_of_installation_id(138);
					nscStagingMigration.setPurpose_of_installation("Demand based Cattle breeding farms");
					if("URBAN".equals(premiseType))
						nscStagingMigration.setSub_category_code(515);
					else if("RURAL".equals(premiseType))
						nscStagingMigration.setSub_category_code(516);				
					else
						throw new Exception("Invalid premise type!");					
				}else
					throw new Exception("Couldn't find suitable subcategory");
			
			}else if(ccnbPurposeOfInstallation.equals("DAIRY_OTHR_AGRI")) {				
				nscStagingMigration.setPurpose_of_installation_id(132);
				nscStagingMigration.setPurpose_of_installation("Dairy milk extraction/chilling/pasteurization");

				if(nscStagingMigration.getSanctioned_load().compareTo(new BigDecimal("25"))<=0 && "HP".equals(nscStagingMigration.getSanctioned_load_unit())) {
					if("URBAN".equals(premiseType))
						nscStagingMigration.setSub_category_code(513);
					else if("RURAL".equals(premiseType))
						nscStagingMigration.setSub_category_code(514);				
					else
						throw new Exception("Invalid premise type!");					
				}else if(nscStagingMigration.getSanctioned_load().compareTo(new BigDecimal("25"))>0 && "HP".equals(nscStagingMigration.getSanctioned_load_unit()) && (ccnbTariffCategory.equals("LV5.3") || ccnbTariffCategory.equals("LV5.3.1"))) {					
					nscStagingMigration.setPurpose_of_installation_id(139);
					nscStagingMigration.setPurpose_of_installation("Demand based Dairy milk extraction/chilling/pasteurization");
					if("URBAN".equals(premiseType))
						nscStagingMigration.setSub_category_code(515);
					else if("RURAL".equals(premiseType))
						nscStagingMigration.setSub_category_code(516);				
					else
						throw new Exception("Invalid premise type!");					
				}else
					throw new Exception("Couldn't find suitable subcategory");

			}else if(ccnbPurposeOfInstallation.equals("FISHERIES_PONDS")) {				
				nscStagingMigration.setPurpose_of_installation_id(126);
				nscStagingMigration.setPurpose_of_installation("Fisheries ponds");

				if(nscStagingMigration.getSanctioned_load().compareTo(new BigDecimal("25"))<=0 && "HP".equals(nscStagingMigration.getSanctioned_load_unit())) {
					if("URBAN".equals(premiseType))
						nscStagingMigration.setSub_category_code(513);
					else if("RURAL".equals(premiseType))
						nscStagingMigration.setSub_category_code(514);				
					else
						throw new Exception("Invalid premise type!");					
				}else if(nscStagingMigration.getSanctioned_load().compareTo(new BigDecimal("25"))>0 && "HP".equals(nscStagingMigration.getSanctioned_load_unit()) && (ccnbTariffCategory.equals("LV5.3") || ccnbTariffCategory.equals("LV5.3.1"))) {					
					nscStagingMigration.setPurpose_of_installation_id(133);
					nscStagingMigration.setPurpose_of_installation("Demand based Fisheries ponds");
					if("URBAN".equals(premiseType))
						nscStagingMigration.setSub_category_code(515);
					else if("RURAL".equals(premiseType))
						nscStagingMigration.setSub_category_code(516);				
					else
						throw new Exception("Invalid premise type!");					
				}else
					throw new Exception("Couldn't find suitable subcategory");

			}else if(ccnbPurposeOfInstallation.equals("HATCHERIES")) {				
				nscStagingMigration.setPurpose_of_installation_id(129);
				nscStagingMigration.setPurpose_of_installation("Hatcheries");

				if(nscStagingMigration.getSanctioned_load().compareTo(new BigDecimal("25"))<=0 && "HP".equals(nscStagingMigration.getSanctioned_load_unit())) {
					if("URBAN".equals(premiseType))
						nscStagingMigration.setSub_category_code(513);
					else if("RURAL".equals(premiseType))
						nscStagingMigration.setSub_category_code(514);				
					else
						throw new Exception("Invalid premise type!");					
				}else if(nscStagingMigration.getSanctioned_load().compareTo(new BigDecimal("25"))>0 && "HP".equals(nscStagingMigration.getSanctioned_load_unit()) && (ccnbTariffCategory.equals("LV5.3") || ccnbTariffCategory.equals("LV5.3.1"))) {					
					nscStagingMigration.setPurpose_of_installation_id(136);
					nscStagingMigration.setPurpose_of_installation("Demand based Hatcheries");
					if("URBAN".equals(premiseType))
						nscStagingMigration.setSub_category_code(515);
					else if("RURAL".equals(premiseType))
						nscStagingMigration.setSub_category_code(516);				
					else
						throw new Exception("Invalid premise type!");					
				}else
					throw new Exception("Couldn't find suitable subcategory");

			}else if(ccnbPurposeOfInstallation.equals("POULTRY_FARMS")) {				
				nscStagingMigration.setPurpose_of_installation_id(130);
				nscStagingMigration.setPurpose_of_installation("Poultry farms");

				if(nscStagingMigration.getSanctioned_load().compareTo(new BigDecimal("25"))<=0 && "HP".equals(nscStagingMigration.getSanctioned_load_unit())) {
					if("URBAN".equals(premiseType))
						nscStagingMigration.setSub_category_code(513);
					else if("RURAL".equals(premiseType))
						nscStagingMigration.setSub_category_code(514);				
					else
						throw new Exception("Invalid premise type!");					
				}else if(nscStagingMigration.getSanctioned_load().compareTo(new BigDecimal("25"))>0 && "HP".equals(nscStagingMigration.getSanctioned_load_unit()) && (ccnbTariffCategory.equals("LV5.3") || ccnbTariffCategory.equals("LV5.3.1"))) {					
					nscStagingMigration.setPurpose_of_installation_id(137);
					nscStagingMigration.setPurpose_of_installation("Demand based Poultry farms");
					if("URBAN".equals(premiseType))
						nscStagingMigration.setSub_category_code(515);
					else if("RURAL".equals(premiseType))
						nscStagingMigration.setSub_category_code(516);				
					else
						throw new Exception("Invalid premise type!");					
				}else
					throw new Exception("Couldn't find suitable subcategory");

			}else if(ccnbPurposeOfInstallation.equals("SERICULTURE")) {				
				nscStagingMigration.setPurpose_of_installation_id(128);
				nscStagingMigration.setPurpose_of_installation("Sericulture");

				if(nscStagingMigration.getSanctioned_load().compareTo(new BigDecimal("25"))<=0 && "HP".equals(nscStagingMigration.getSanctioned_load_unit())) {
					if("URBAN".equals(premiseType))
						nscStagingMigration.setSub_category_code(513);
					else if("RURAL".equals(premiseType))
						nscStagingMigration.setSub_category_code(514);				
					else
						throw new Exception("Invalid premise type!");					
				}else if(nscStagingMigration.getSanctioned_load().compareTo(new BigDecimal("25"))>0 && "HP".equals(nscStagingMigration.getSanctioned_load_unit()) && (ccnbTariffCategory.equals("LV5.3") || ccnbTariffCategory.equals("LV5.3.1"))) {					
					nscStagingMigration.setPurpose_of_installation_id(135);
					nscStagingMigration.setPurpose_of_installation("Demand based Sericulture");
					if("URBAN".equals(premiseType))
						nscStagingMigration.setSub_category_code(515);
					else if("RURAL".equals(premiseType))
						nscStagingMigration.setSub_category_code(516);				
					else
						throw new Exception("Invalid premise type!");					
				}else
					throw new Exception("Couldn't find suitable subcategory");

			}else if(ccnbPurposeOfInstallation.equals("OTHER_AGRI")) {				
				nscStagingMigration.setPurpose_of_installation_id(130);
				nscStagingMigration.setPurpose_of_installation("Poultry farms");

				if(nscStagingMigration.getSanctioned_load().compareTo(new BigDecimal("25"))<=0 && "HP".equals(nscStagingMigration.getSanctioned_load_unit())) {
					if("URBAN".equals(premiseType))
						nscStagingMigration.setSub_category_code(513);
					else if("RURAL".equals(premiseType))
						nscStagingMigration.setSub_category_code(514);				
					else
						throw new Exception("Invalid premise type!");					
				}else if(nscStagingMigration.getSanctioned_load().compareTo(new BigDecimal("25"))>0 && "HP".equals(nscStagingMigration.getSanctioned_load_unit()) && (ccnbTariffCategory.equals("LV5.3") || ccnbTariffCategory.equals("LV5.3.1"))) {					
					nscStagingMigration.setPurpose_of_installation_id(137);
					nscStagingMigration.setPurpose_of_installation("Demand based Poultry farms");
					if("URBAN".equals(premiseType))
						nscStagingMigration.setSub_category_code(515);
					else if("RURAL".equals(premiseType))
						nscStagingMigration.setSub_category_code(516);				
					else
						throw new Exception("Invalid premise type!");					
				}else
					throw new Exception("Couldn't find suitable subcategory");

			}else
				throw new Exception("No suitable purpose of installation found!");
			
			nscStagingMigration.setTariff_code("LV5.3");							

		}else if(ccnbTariffCategory.equals("LV5.4") && (ccnbPurposeOfInstallationCd.equals("CM_USAGE") || ccnbPurposeOfInstallationCd.equals("CM_USAGO")) && (ccnbPurposeOfInstallation.equals("AGRB") || ccnbPurposeOfInstallation.equals("AGRI_FLAT") || ccnbPurposeOfInstallation.equals("AGRI_PUMP"))) {
			nscStagingMigration.setPurpose_of_installation_id(101);
			nscStagingMigration.setPurpose_of_installation("(FLAT RATE) Permanent agricultural pump");
			nscStagingMigration.setTariff_code("LV5.4");

			if(phase.equals("THREE")) {
				if("URBAN".equals(premiseType))
					nscStagingMigration.setSub_category_code(511);
				else if("RURAL".equals(premiseType))
					nscStagingMigration.setSub_category_code(512);
				else
					throw new Exception("Invalid premise type!");
			}else if(phase.equals("SINGLE")) {
				if("URBAN".equals(premiseType))
					nscStagingMigration.setSub_category_code(509);
				else if("RURAL".equals(premiseType))
					nscStagingMigration.setSub_category_code(510);				
				else
					throw new Exception("Invalid premise type!");
			}else
				throw new Exception("Invalid phase!");			
		}else
			throw new Exception("Couldn't find a suitable tariff for the consumer!");
	}

	private static void decideTariffFromTariffMapping(NSCStagingMigration nscStagingMigration, CCNBNSCStagingMigration currentRecord, CcnbNgbTariffMapping tariffMapping) throws Exception {
		nscStagingMigration.setTariff_code(tariffMapping.getNgbTariffCode());
		
		nscStagingMigration.setPurpose_of_installation(tariffMapping.getNgbPurposeOfInstallation());
		
		nscStagingMigration.setPurpose_of_installation_id(Long.parseLong(tariffMapping.getNgbPurposeId()));
		
		//Subcategory code only handeled for domestic as of now
		if(currentRecord.getOld_trf_catg().trim().startsWith("LV1.1")) {
			if(currentRecord.getSanctioned_load().setScale(1, RoundingMode.HALF_UP).compareTo(new BigDecimal("0.1"))>0)
				throw new Exception("Load can't be greater than 0.1 for LV1.1 !");
		}
		
		if(currentRecord.getOld_trf_catg().trim().startsWith("LV1")) {
			
			if(!"KW".equals(currentRecord.getSanctioned_load_unit()))
				throw new Exception("Invalid sanctioned load unit for LV1 tariff category !!");

			if("URBAN".equals(currentRecord.getPremise_type()))
				nscStagingMigration.setSub_category_code(Long.parseLong(tariffMapping.getNgbUrbanSubcategory1()));
			else if("RURAL".equals(currentRecord.getPremise_type()))
				nscStagingMigration.setSub_category_code(Long.parseLong(tariffMapping.getNgbRuralSubcategory1()));
			
		}else if(currentRecord.getOld_trf_catg().trim().startsWith("LV3")) {
			if("CM_IPMPW".equals(currentRecord.getSaType()) || "CM_BPMPW".equals(currentRecord.getSaType()) || "CM_JPMPW".equals(currentRecord.getSaType())) {
				/////////////////////////////////////////////////////////////
			}
		}else if(currentRecord.getOld_trf_catg().trim().startsWith("LV2")) {
			
			if(!"KW".equals(currentRecord.getSanctioned_load_unit()))
				throw new Exception("Invalid sanctioned load unit for LV2 tariff category !!");

			if(currentRecord.getSanctioned_load().compareTo(new BigDecimal("10"))<=0) {
				if("URBAN".equals(currentRecord.getPremise_type()))
					nscStagingMigration.setSub_category_code(Long.parseLong(tariffMapping.getNgbUrbanSubcategory1()));
				else if("RURAL".equals(currentRecord.getPremise_type()))
					nscStagingMigration.setSub_category_code(Long.parseLong(tariffMapping.getNgbRuralSubcategory1()));						
				
			}else {
				
				if(nscStagingMigration.getContract_demand().compareTo(BigDecimal.ZERO)==0)
					throw new Exception("Contract demand can't be 0 !");
				if("URBAN".equals(currentRecord.getPremise_type()))
					nscStagingMigration.setSub_category_code(Long.parseLong(tariffMapping.getNgbUrbanSubcategory2()));
				else if("RURAL".equals(currentRecord.getPremise_type()))
					nscStagingMigration.setSub_category_code(Long.parseLong(tariffMapping.getNgbRuralSubcategory2()));												
				
			}						
		}else if(currentRecord.getOld_trf_catg().trim().startsWith("LV4")) {
			
			if(!"HP".equals(currentRecord.getSanctioned_load_unit()))
				throw new Exception("Invalid sanctioned load unit for LV4 tariff category !!");
			if(nscStagingMigration.getContract_demand().compareTo(BigDecimal.ZERO)==0)
				throw new Exception("Contract demand can't be 0 !");
			
			if(nscStagingMigration.getContract_demand().setScale(2, RoundingMode.HALF_UP).compareTo(new BigDecimal("14.92"))<=0) {
				if("URBAN".equals(currentRecord.getPremise_type()))
					nscStagingMigration.setSub_category_code(Long.parseLong(tariffMapping.getNgbUrbanSubcategory1()));
				else if("RURAL".equals(currentRecord.getPremise_type()))
					nscStagingMigration.setSub_category_code(Long.parseLong(tariffMapping.getNgbRuralSubcategory1()));													
			}else {
				if("URBAN".equals(currentRecord.getPremise_type()))
					nscStagingMigration.setSub_category_code(Long.parseLong(tariffMapping.getNgbUrbanSubcategory2()));
				else if("RURAL".equals(currentRecord.getPremise_type()))
					nscStagingMigration.setSub_category_code(Long.parseLong(tariffMapping.getNgbRuralSubcategory2()));									
			}
			
			
		}else if(currentRecord.getOld_trf_catg().trim().startsWith("LV5")) {

			if("URBAN".equals(currentRecord.getPremise_type()))
				nscStagingMigration.setSub_category_code(Long.parseLong(tariffMapping.getNgbUrbanSubcategory1()));
			else if("RURAL".equals(currentRecord.getPremise_type()))
				nscStagingMigration.setSub_category_code(Long.parseLong(tariffMapping.getNgbRuralSubcategory1()));
					
		}else
			throw new Exception("Couldn't find a suitable tariff for the consumer!");
	}
	
	private static CCNBMeterMapping getCcnbMeterMapping(String meterCapacity, List<CCNBMeterMapping> meterMappings) {
		CCNBMeterMapping meterMapping = null;
		for(CCNBMeterMapping obj: meterMappings) {
			if(obj.getCcnbMeterCapacity().equals(meterCapacity.trim()))
				meterMapping = obj;
		}
		return meterMapping;
	}
	
	private static CcnbNgbTariffMapping getLV1TariffMapping(List<CcnbNgbTariffMapping> tariffMappings, String ccnbTariffCategory){
		CcnbNgbTariffMapping ccnbNgbTariffMapping  = null;
		for(CcnbNgbTariffMapping tariff: tariffMappings) {
			if(tariff.getCcnbTariff().trim().equals(ccnbTariffCategory))
				return tariff;
		}		
		return ccnbNgbTariffMapping;
	}
}
